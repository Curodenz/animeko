/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.httpdownloader

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.httpdownloader.DownloadStatus.*
import me.him188.ani.utils.httpdownloader.m3u.DefaultM3u8Parser
import me.him188.ani.utils.httpdownloader.m3u.M3u8Parser
import me.him188.ani.utils.httpdownloader.m3u.M3u8Playlist
import me.him188.ani.utils.io.DEFAULT_BUFFER_SIZE
import me.him188.ani.utils.io.copyTo
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.ktor.ScopedHttpClient
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.platform.Uuid
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.CoroutineContext

/**
 * A simple implementation of [HttpDownloader] that uses Ktor and coroutines.
 * Supports both M3u8 streams and regular media files.
 */
open class KtorHttpDownloader(
    protected val client: ScopedHttpClient,
    private val fileSystem: FileSystem,
    computeDispatcher: CoroutineContext = Dispatchers.Default,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
    val clock: Clock = Clock.System,
    private val m3u8Parser: M3u8Parser = DefaultM3u8Parser,
) : HttpDownloader {

    protected val scope = CoroutineScope(SupervisorJob() + computeDispatcher)

    private val _progressFlow = MutableSharedFlow<DownloadProgress>(
        replay = 1,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val progressFlow: Flow<DownloadProgress> = _progressFlow.asSharedFlow()

    override fun getProgressFlow(downloadId: DownloadId): Flow<DownloadProgress> {
        return progressFlow.filter { it.downloadId == downloadId }.onStart {
            emit(
                createProgress(getState(downloadId) ?: return@onStart),
            )
        }
    }

    /**
     * Our map of download states.
     */
    protected val _downloadStatesFlow = MutableStateFlow(emptyMap<DownloadId, DownloadEntry>())

    override val downloadStatesFlow: Flow<List<DownloadState>> =
        _downloadStatesFlow.map { it.values.map { entry -> entry.state } }

    override suspend fun init() {
        // No initialization needed, but place for potential future logic
        logger.info { "KtorHttpDownloader initialized." }
    }

    protected val stateMutex = Mutex()

    override suspend fun download(
        url: String,
        outputPath: Path,
        options: DownloadOptions,
    ): DownloadId {
        val downloadId = DownloadId(value = generateDownloadId(url))
        downloadWithId(downloadId, url, outputPath, options)
        return downloadId
    }

    protected fun getMediaTypeFromUrl(url: String): MediaType? {
        return when {
            url.endsWith(".m3u8", ignoreCase = true) -> MediaType.M3U8
            url.endsWith(".mp4", ignoreCase = true) -> MediaType.MP4
            url.endsWith(".mkv", ignoreCase = true) -> MediaType.MKV
            else -> null
        }
    }

    override suspend fun downloadWithId(
        downloadId: DownloadId,
        url: String,
        outputPath: Path,
        options: DownloadOptions,
    ) {
        val mediaType = getMediaTypeFromUrl(url) ?: MediaType.M3U8
        logger.info { "Preparing to download with id=$downloadId, url=$url, mediaType=$mediaType" }

        stateMutex.withLock {
            val currentMap = _downloadStatesFlow.value.toMutableMap()
            val existing = currentMap[downloadId]
            if (existing != null) {
                // If there's already a state for this downloadId in COMPLETED, do nothing
                logger.info { "Existing completed download found for $downloadId, ignoring." }
                return
            }
            val segmentCacheDir = createSegmentCacheDir(outputPath, downloadId)
            val initialState = DownloadState(
                downloadId = downloadId,
                url = url,
                outputPath = outputPath.toString(),
                segments = emptyList(),
                totalSegments = 0,
                downloadedBytes = 0L,
                timestamp = clock.now().toEpochMilliseconds(),
                status = INITIALIZING,
                segmentCacheDir = segmentCacheDir.toString(),
                mediaType = mediaType,
            )
            currentMap[downloadId] = DownloadEntry(job = null, state = initialState)
            _downloadStatesFlow.value = currentMap

            logger.info { "Starting download '$url', initial state: $initialState" }
        }

        emitProgress(downloadId)

        // -----------------------------------------------------------
        // (1) Create segments *before* launching the job
        // -----------------------------------------------------------
        val segments: List<SegmentInfo> = try {
            when (mediaType) {
                MediaType.M3U8 -> {
                    logger.info { "Resolving M3U8 media playlist for $downloadId" }
                    val playlist = resolveM3u8MediaPlaylist(url, options)
                    playlist.toSegments(Path(getState(downloadId)?.segmentCacheDir ?: return))
                }

                MediaType.MP4, MediaType.MKV -> {
                    logger.info { "Creating range segments for $downloadId" }
                    createRangeSegments(downloadId, url, options)
                }
            }
        } catch (e: Throwable) {
            // If segment creation fails (404, parse error, etc.), mark FAILED
            logger.info { "Segment creation failed for $downloadId: ${e.message}" }
            updateState(downloadId) {
                it.copy(
                    status = FAILED,
                    error = DownloadError(
                        code = if (e is M3u8Exception) e.errorCode else DownloadErrorCode.UNEXPECTED_ERROR,
                        technicalMessage = e.message,
                    ),
                    timestamp = clock.now().toEpochMilliseconds()
                )
            }
            emitProgress(downloadId)
            return
        }

        updateState(downloadId) {
            it.copy(
                segments = segments,
                totalSegments = segments.size,
                status = DOWNLOADING
            )
        }
        emitProgress(downloadId)
        logger.info { "Created ${segments.size} segments for $downloadId" }

        // -----------------------------------------------------------
        // (2) Launch the coroutine that uses those segments
        // -----------------------------------------------------------
        val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                logger.info { "Downloading segments for $downloadId" }
                downloadSegments(downloadId, options)

                updateState(downloadId) {
                    it.copy(status = MERGING, timestamp = clock.now().toEpochMilliseconds())
                }
                emitProgress(downloadId)
                logger.info { "Merging segments for $downloadId into final output" }

                mergeSegments(downloadId)

                updateState(downloadId) {
                    it.copy(status = COMPLETED, timestamp = clock.now().toEpochMilliseconds())
                }
                emitProgress(downloadId)
                logger.info { "Download completed for $downloadId" }

            } catch (e: CancellationException) {
                // Normal cancellation
                logger.info { "Download cancelled for $downloadId" }
                throw e
            } catch (e: Throwable) {
                // Mark FAILED
                logger.info { "Download failed for $downloadId: ${e.message}" }
                updateState(downloadId) {
                    it.copy(
                        status = FAILED,
                        error = DownloadError(
                            code = if (e is M3u8Exception) e.errorCode else DownloadErrorCode.UNEXPECTED_ERROR,
                            technicalMessage = e.message,
                        ),
                        timestamp = clock.now().toEpochMilliseconds(),
                    )
                }
                emitProgress(downloadId)
            }
        }

        // Store the newly-created job
        stateMutex.withLock {
            val currentMap = _downloadStatesFlow.value.toMutableMap()
            val entry = currentMap[downloadId]
            if (entry != null) {
                currentMap[downloadId] = entry.copy(job = job)
                _downloadStatesFlow.value = currentMap
            }
        }
    }

    override suspend fun resume(downloadId: DownloadId): Boolean {
        val st = getState(downloadId) ?: return false
        if (st.status != PAUSED && st.status != FAILED) {
            logger.info { "Cannot resume $downloadId because status=${st.status}" }
            return false
        }

        // Check if there's already an active job
        val hasExistingJob = stateMutex.withLock {
            val existing = _downloadStatesFlow.value[downloadId]
            existing != null && existing.job?.isActive == true
        }
        if (hasExistingJob) {
            logger.info { "Attempting to resume $downloadId but there's already an active job." }
            emitProgress(downloadId)
            return true
        }

        logger.info { "Resuming $downloadId with status=${st.status}" }
        updateState(downloadId) { it.copy(status = DOWNLOADING) }
        emitProgress(downloadId)

        val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                logger.info { "Resumed: downloading segments for $downloadId" }
                downloadSegments(downloadId, DownloadOptions())
                updateState(downloadId) {
                    it.copy(status = MERGING)
                }
                emitProgress(downloadId)
                logger.info { "Merging segments for resumed $downloadId" }
                mergeSegments(downloadId)
                updateState(downloadId) {
                    it.copy(status = COMPLETED)
                }
                emitProgress(downloadId)
                logger.info { "Download completed after resume for $downloadId" }
            } catch (e: CancellationException) {
                logger.info { "Download cancelled while resumed for $downloadId" }
                throw e
            } catch (t: Throwable) {
                logger.info { "Resumed download failed for $downloadId: ${t.message}" }
                updateState(downloadId) {
                    it.copy(
                        status = FAILED,
                        error = DownloadError(DownloadErrorCode.UNEXPECTED_ERROR, technicalMessage = t.message),
                    )
                }
                emitProgress(downloadId)
            }
        }
        stateMutex.withLock {
            val currentMap = _downloadStatesFlow.value.toMutableMap()
            val entry = currentMap[downloadId]
            if (entry != null) {
                currentMap[downloadId] = entry.copy(job = job)
                _downloadStatesFlow.value = currentMap
            }
        }
        return true
    }

    override suspend fun getActiveDownloadIds(): List<DownloadId> {
        return stateMutex.withLock {
            _downloadStatesFlow.value.values
                .filter { it.state.status == DOWNLOADING || it.state.status == INITIALIZING }
                .map { it.state.downloadId }
        }
    }

    override suspend fun pause(downloadId: DownloadId): Boolean {
        stateMutex.withLock {
            val currentMap = _downloadStatesFlow.value.toMutableMap()
            val entry = currentMap[downloadId] ?: return false
            val job = entry.job
            if (job != null) {
                if (!job.isActive) {
                    logger.info { "Cannot pause $downloadId because job is not active." }
                    return false
                }
                logger.info { "Pausing download $downloadId" }
                job.cancel()
            }

            val oldState = entry.state
            currentMap[downloadId] = entry.copy(
                job = null,
                state = oldState.copy(status = PAUSED),
            )
            _downloadStatesFlow.value = currentMap
        }
        emitProgress(downloadId)
        return true
    }

    override suspend fun pauseAll(): List<DownloadId> {
        val paused = mutableListOf<DownloadId>()
        stateMutex.withLock {
            val currentMap = _downloadStatesFlow.value.toMutableMap()
            currentMap.forEach { (id, entry) ->
                val job = entry.job
                if (job != null && job.isActive) {
                    logger.info { "Pausing download $id" }
                    job.cancel()
                    currentMap[id] = entry.copy(
                        job = null,
                        state = entry.state.copy(status = PAUSED),
                    )
                    paused.add(id)
                }
            }
            _downloadStatesFlow.value = currentMap
        }
        paused.forEach { emitProgress(it) }
        return paused
    }

    override suspend fun cancel(downloadId: DownloadId): Boolean {
        stateMutex.withLock {
            val currentMap = _downloadStatesFlow.value.toMutableMap()
            val entry = currentMap[downloadId] ?: return false
            val job = entry.job
            if (job != null && job.isActive) {
                logger.info { "Cancelling download $downloadId" }
                job.cancel()
            }
            currentMap[downloadId] = entry.copy(
                job = null,
                state = entry.state.copy(status = CANCELED),
            )
            _downloadStatesFlow.value = currentMap
        }
        emitProgress(downloadId)
        return true
    }

    override suspend fun cancelAll() {
        logger.info { "Cancelling all downloads." }
        stateMutex.withLock {
            val currentMap = _downloadStatesFlow.value.toMutableMap()
            currentMap.forEach { (id, entry) ->
                if (entry.job?.isActive == true) {
                    logger.info { "Cancelling download $id" }
                    entry.job.cancel()
                }
                val st = entry.state
                if (st.status in listOf(INITIALIZING, DOWNLOADING, PAUSED, MERGING)) {
                    currentMap[id] = entry.copy(
                        job = null,
                        state = st.copy(status = CANCELED),
                    )
                }
            }
            _downloadStatesFlow.value = currentMap
        }
        val allIds = stateMutex.withLock { _downloadStatesFlow.value.keys.toList() }
        allIds.forEach { emitProgress(it) }
    }

    override suspend fun getState(downloadId: DownloadId): DownloadState? {
        return stateMutex.withLock {
            _downloadStatesFlow.value[downloadId]?.state
        }
    }

    override suspend fun getAllStates(): List<DownloadState> {
        return stateMutex.withLock {
            _downloadStatesFlow.value.values.map { it.state }
        }
    }

    override fun close() {
        logger.info { "Closing KtorHttpDownloader." }
        scope.launch(NonCancellable + CoroutineName("M3u8Downloader.close")) {
            closeSuspend()
        }
    }

    suspend fun closeSuspend() {
        logger.info { "closeSuspend() called; joining all active jobs." }
        stateMutex.withLock {
            val currentMap = _downloadStatesFlow.value
            currentMap.forEach { (_, entry) ->
                if (entry.job?.isActive == true) {
                    entry.job.cancelAndJoin()
                }
            }
            _downloadStatesFlow.value = emptyMap()
        }
        scope.cancel()
        logger.info { "KtorHttpDownloader closed." }
    }

    // -------------------------------------------------------
    // Internal details
    // -------------------------------------------------------

    protected data class DownloadEntry(val job: Job?, val state: DownloadState)

    /**
     * Create and return the directory in which segment files will be stored.
     */
    protected suspend fun createSegmentCacheDir(
        outputPath: Path,
        downloadId: DownloadId,
    ): Path = withContext(ioDispatcher) {
        val cacheDirName = outputPath.name + "_segments_" + downloadId.value
        val parentDir = outputPath.parent ?: Path(".")
        val cacheDir = parentDir.resolve(cacheDirName)
        fileSystem.createDirectories(cacheDir)
        logger.info { "Created segment cache dir for $downloadId at $cacheDir" }
        cacheDir
    }

    /**
     * Recursively resolves a MasterPlaylist to a MediaPlaylist (if needed).
     */
    private suspend fun resolveM3u8MediaPlaylist(
        url: String,
        options: DownloadOptions,
        depth: Int = 0,
    ): M3u8Playlist.MediaPlaylist {
        if (depth >= 5) {
            throw M3u8Exception(DownloadErrorCode.NO_MEDIA_LIST)
        }
        logger.info { "Resolving M3U8 playlist from $url (depth=$depth)" }
        val response = httpGet(url, options) { it.body<String>() }
        return when (val playlist = m3u8Parser.parse(response, url)) {
            is M3u8Playlist.MasterPlaylist -> {
                val bestVariant = playlist.variants.maxByOrNull { it.bandwidth }
                    ?: throw M3u8Exception(DownloadErrorCode.NO_MEDIA_LIST)
                logger.info { "Master playlist found; picking highest bandwidth variant=${bestVariant.bandwidth}" }
                resolveM3u8MediaPlaylist(bestVariant.uri, options, depth + 1)
            }

            is M3u8Playlist.MediaPlaylist -> {
                logger.info { "Media playlist resolved at depth=$depth for $url" }
                playlist
            }
        }
    }

    protected suspend fun updateState(downloadId: DownloadId, transform: (DownloadState) -> DownloadState) {
        stateMutex.withLock {
            val currentMap = _downloadStatesFlow.value.toMutableMap()
            val entry = currentMap[downloadId] ?: return
            val oldState = entry.state
            val newState = transform(oldState)
            currentMap[downloadId] = entry.copy(state = newState)
            _downloadStatesFlow.value = currentMap
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    protected suspend fun createRangeSegments(
        downloadId: DownloadId,
        url: String,
        options: DownloadOptions,
    ): List<SegmentInfo> {
        val cacheDir = Path(getState(downloadId)?.segmentCacheDir ?: error("Cache dir not found"))
        val rangeProbe = probeRangeSupport(url, options)

        // If the probe fails or the server doesn't support range => single segment
        val (contentLength, rangeSupported) = rangeProbe
            ?: return listOf(
                SegmentInfo(
                    index = 0,
                    url = url,
                    isDownloaded = false,
                    byteSize = -1,
                    tempFilePath = cacheDir.resolve("0.part").toString(),
                    rangeStart = null,
                    rangeEnd = null,
                )
            )

        if (!rangeSupported) {
            logger.info { "Range not supported for $downloadId, creating single segment." }
            return listOf(
                SegmentInfo(
                    index = 0,
                    url = url,
                    isDownloaded = false,
                    byteSize = contentLength, // might be -1 if unknown
                    tempFilePath = cacheDir.resolve("0.part").toString(),
                    rangeStart = null,
                    rangeEnd = null,
                )
            )
        }

        logger.info { "Range supported for $downloadId, total file size: $contentLength" }
        val segmentSize = 5 * 1024 * 1024L // 5MB
        if (contentLength <= segmentSize) {
            logger.info { "File is smaller than $segmentSize for $downloadId, single segment." }
            return listOf(
                SegmentInfo(
                    index = 0,
                    url = url,
                    isDownloaded = false,
                    byteSize = contentLength,
                    tempFilePath = cacheDir.resolve("0.part").toString(),
                    rangeStart = 0,
                    rangeEnd = contentLength - 1,
                )
            )
        }

        logger.info { "Splitting file into segments of size=$segmentSize for $downloadId" }
        val segments = mutableListOf<SegmentInfo>()
        var start = 0L
        var index = 0
        while (start < contentLength) {
            val end = (start + segmentSize - 1).coerceAtMost(contentLength - 1)
            segments.add(
                SegmentInfo(
                    index = index,
                    url = url,
                    isDownloaded = false,
                    byteSize = (end - start + 1),
                    tempFilePath = cacheDir.resolve("$index.part").toString(),
                    rangeStart = start,
                    rangeEnd = end,
                )
            )
            start = end + 1
            index++
        }

        logger.info { "Created ${segments.size} range segments for $downloadId" }
        return segments
    }

    /**
     * Attempt a small GET with Range=0-0 to detect whether partial content is supported
     * and to parse the total file size from 'Content-Range: bytes 0-0/<size>'.
     */
    private suspend fun probeRangeSupport(
        url: String,
        options: DownloadOptions,
    ): Pair<Long, Boolean>? {
        val rangeOptions = options.copy(
            headers = options.headers + ("Range" to "bytes=0-0")
        )
        return try {
            client.use {
                prepareGet(url) {
                    rangeOptions.headers.forEach { (k, v) -> header(k, v) }
                }.execute { response ->
                    when (response.status.value) {
                        206 -> {
                            val contentRange =
                                response.headers[io.ktor.http.HttpHeaders.ContentRange] ?: return@execute null
                            val totalSize = contentRange.substringAfter('/').toLongOrNull() ?: return@execute null
                            totalSize to true // (size, range supported)
                        }

                        200 -> {
                            val length = response.headers[io.ktor.http.HttpHeaders.ContentLength]?.toLongOrNull() ?: -1L
                            length to false
                        }

                        else -> null
                    }
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    protected suspend fun downloadSingleSegment(
        segmentInfo: SegmentInfo,
        options: DownloadOptions,
    ): Long {
        // If we have a range, add it to the request headers.
        val finalOptions = if (segmentInfo.rangeStart != null && segmentInfo.rangeEnd != null) {
            options.copy(
                headers = options.headers + ("Range" to "bytes=${segmentInfo.rangeStart}-${segmentInfo.rangeEnd}")
            )
        } else {
            options
        }
        logger.info { "Downloading segment index=${segmentInfo.index}, range=(${segmentInfo.rangeStart}-${segmentInfo.rangeEnd})" }

        return httpGet(segmentInfo.url, finalOptions) { statement ->
            val response = statement.execute()
            val channel = response.bodyAsChannel()

            val segmentPath = Path(segmentInfo.tempFilePath)
            withContext(ioDispatcher) {
                fileSystem.createDirectories(
                    segmentPath.parent ?: error("Parent dir not found for segmentInfo: $segmentInfo")
                )
            }

            copyChannelToFile(channel, segmentPath).also {
                logger.info { "Segment index=${segmentInfo.index} downloaded, size=$it" }
            }
        }
    }

    /**
     * Reads from [channel] and writes to [filePath], returning the total bytes downloaded.
     */
    @OptIn(ExperimentalAtomicApi::class)
    private suspend fun copyChannelToFile(
        channel: ByteReadChannel,
        filePath: Path,
    ): Long {
        val totalBytes = AtomicLong(0L)
        fileSystem.sink(filePath).buffered().use { sink ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            withContext(ioDispatcher) {
                while (true) {
                    val bytesRead = channel.readAvailable(buffer, 0, buffer.size)
                    if (bytesRead == -1) break
                    sink.write(buffer, startIndex = 0, endIndex = bytesRead)
                    totalBytes.fetchAndAdd(bytesRead.toLong())
                }
            }
        }
        return totalBytes.load()
    }

    protected suspend fun downloadSegments(downloadId: DownloadId, options: DownloadOptions) {
        val snapshot = getState(downloadId) ?: return
        if (snapshot.segments.isEmpty()) {
            logger.info { "No segments to download for $downloadId" }
            return
        }
        logger.info { "Downloading ${snapshot.segments.size} segments for $downloadId with concurrency=${options.maxConcurrentSegments}" }
        val semaphore = Semaphore(options.maxConcurrentSegments)

        coroutineScope {
            snapshot.segments.forEach { seg ->
                if (seg.isDownloaded) {
                    return@forEach
                }
                semaphore.acquire()
                launch(ioDispatcher, start = CoroutineStart.ATOMIC) {
                    try {
                        val newSize = downloadSingleSegment(seg, options)
                        markSegmentDownloaded(downloadId, seg.index, newSize)
                    } finally {
                        semaphore.release()
                    }
                }
            }
        }
        logger.info { "All segments downloaded for $downloadId" }
    }

    protected suspend fun markSegmentDownloaded(downloadId: DownloadId, segmentIndex: Int, byteSize: Long) {
        logger.info { "Segment index=$segmentIndex fully downloaded for $downloadId, size=$byteSize" }
        updateState(downloadId) { old ->
            val updatedSegments = old.segments.map {
                if (it.index == segmentIndex) it.copy(isDownloaded = true, byteSize = byteSize) else it
            }
            old.copy(downloadedBytes = old.downloadedBytes + byteSize, segments = updatedSegments)
        }
        emitProgress(downloadId)
    }

    protected suspend fun mergeSegments(downloadId: DownloadId) = withContext(ioDispatcher) {
        val st = getState(downloadId) ?: return@withContext
        val cacheDir = Path(st.segmentCacheDir)
        val finalOutput = Path(st.outputPath)

        fileSystem.sink(finalOutput).buffered().use { out ->
            st.segments.sortedBy { it.index }.forEach { seg ->
                fileSystem.source(Path(seg.tempFilePath)).buffered().use { input ->
                    input.copyTo(out)
                }
            }
        }

        // remove segment files
        st.segments.forEach { seg ->
            fileSystem.delete(Path(seg.tempFilePath))
        }
        // remove the cache dir
        fileSystem.delete(cacheDir)
        logger.info { "Segments merged into $finalOutput, removed cache dir=$cacheDir for $downloadId" }
    }

    protected suspend fun emitProgress(downloadId: DownloadId) {
        val st = getState(downloadId) ?: return
        val progress = createProgress(st)
        _progressFlow.emit(progress)
    }

    private fun createProgress(st: DownloadState): DownloadProgress {
        val downloadedSegments = st.segments.count { it.isDownloaded }
        return DownloadProgress(
            downloadId = st.downloadId,
            url = st.url,
            totalSegments = st.totalSegments,
            downloadedSegments = downloadedSegments,
            downloadedBytes = st.downloadedBytes,
            totalBytes = st.segments.sumOf { it.byteSize.coerceAtLeast(0) }
                .coerceAtLeast(st.downloadedBytes),
            status = st.status,
            error = st.error,
        )
    }

    protected suspend inline fun <R> httpGet(url: String, options: DownloadOptions, block: (HttpStatement) -> R): R {
        return client.use {
            prepareGet(url) {
                options.headers.forEach { (k, v) -> header(k, v) }
            }.let { statement -> block(statement) }
        }
    }

    protected fun generateDownloadId(url: String): String {
        return Uuid.randomString()
    }

    suspend fun joinDownload(downloadId: DownloadId) {
        val job = stateMutex.withLock {
            _downloadStatesFlow.value[downloadId]?.job
        }
        if (job != null) {
            logger.info { "Waiting for download job to complete for $downloadId" }
            job.join()
            logger.info { "Download job completed for $downloadId" }
        }
    }

    private companion object {
        val logger = logger<KtorHttpDownloader>()
    }
}

private class M3u8Exception(val errorCode: DownloadErrorCode) : Exception()

private fun M3u8Playlist.MediaPlaylist.toSegments(cacheDir: Path): List<SegmentInfo> {
    return segments.mapIndexed { i, seg ->
        val idx = mediaSequence + i
        SegmentInfo(
            index = idx,
            url = seg.uri,
            isDownloaded = false,
            byteSize = seg.byteRange?.length ?: -1,
            tempFilePath = cacheDir.resolve("$idx.ts").toString(),
        )
    }
}
