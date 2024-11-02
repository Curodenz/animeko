/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.subject

import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import me.him188.ani.app.data.models.subject.FollowedSubjectInfo
import me.him188.ani.app.data.models.subject.SubjectAiringInfo
import me.him188.ani.app.data.models.subject.SubjectProgressInfo
import me.him188.ani.app.data.models.subject.hasNewEpisodeToPlay
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.data.repository.episode.EpisodeCollectionRepository
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.coroutines.IO_
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * 用户正在追的条目仓库
 */
class FollowedSubjectsRepository(
    private val subjectCollectionRepository: SubjectCollectionRepository,
    private val episodeCollectionRepository: EpisodeCollectionRepository,
//    private val subjectProgressRepository: EpisodeProgressRepository,
//    private val subjectCollectionDao: SubjectCollectionDao,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
) {
    private fun followedSubjectsFlow(
        updatePeriod: Duration = 1.hours,
    ): Flow<List<FollowedSubjectInfo>> {
        require(updatePeriod > Duration.ZERO) { "updatePeriod must be positive" }

        val ticker = flow {
            while (true) {
                emit(Unit)
                kotlinx.coroutines.delay(updatePeriod)
            }
        }

        val now = PackedDate.now()

        // 对于最近看过的一些条目
        return subjectCollectionRepository.mostRecentlyUpdatedSubjectCollectionsFlow(
            limit = 64,
            types = listOf(
                UnifiedCollectionType.DOING,
            ),
        ).combineTransform(ticker) { subjectCollectionInfoList, _ ->
            // 对于每个条目, 获取其最新的集数信息
            val subjectProgressInfos = combine(
                subjectCollectionInfoList.map { info ->
                    episodeCollectionRepository.subjectEpisodeCollectionInfosFlow(info.subjectId)
                },
            ) { array ->
                subjectCollectionInfoList.asSequence()
                    .zip(array.asSequence()) { subjectCollectionInfo, episodes ->
                        // 计算每个条目的播放进度
                        FollowedSubjectInfo(
                            subjectCollectionInfo,
                            SubjectAiringInfo.computeFromEpisodeList(
                                episodes.map { it.episodeInfo },
                                subjectCollectionInfo.subjectInfo.airDate,
                            ),
                            SubjectProgressInfo.compute(
                                subjectCollectionInfo.subjectInfo,
                                episodes,
                                now,
                            ),
                        )
                    }.toList()
            }
            emit(emptyList())
            return@combineTransform
            emitAll(subjectProgressInfos)
        }.map { followedSubjectInfoList ->
            followedSubjectInfoList
                .toMutableList()
                .apply {
                    sortWith(sorter)
                }
        }.catch {
            RepositoryException.wrapOrThrowCancellation(it)
        }.flowOn(ioDispatcher)
    }

    fun followedSubjectsPager(
        updatePeriod: Duration = 1.hours,
    ) = followedSubjectsFlow(updatePeriod)
        .map {
            PagingData.from(
                it,
                NotLoading,
            )
        }.catch { e ->
            emit(
                PagingData.empty(
                    sourceLoadStates = LoadStates(
                        refresh = LoadState.NotLoading(true),
                        prepend = LoadState.NotLoading(true),
                        append = LoadState.Error(e),
                    ),
                ),
            )
        }

    private companion object {
        private val NotLoading = LoadStates(
            refresh = LoadState.NotLoading(true),
            prepend = LoadState.NotLoading(true),
            append = LoadState.NotLoading(true),
        )

        val sorter: Comparator<FollowedSubjectInfo> =
            // 不要用最后访问时间排序, 因为刷新后时间会乱
            compareByDescending<FollowedSubjectInfo> { info ->
                // 1. 现在可以看的 > 现在不能看的
                info.subjectProgressInfo.hasNewEpisodeToPlay
            }.thenByDescending { info ->
                // 2. 在看 > 想看
                info.subjectCollectionInfo.collectionType == UnifiedCollectionType.DOING
            }.thenByDescending { info -> // TODO: 3. 最后播放时间降序 (不必在 4.0 实现) 
                // 4. (已经看了的 sort - first sort) 降序
                val firstEp = info.subjectCollectionInfo.episodes.firstOrNull()?.episodeInfo?.sort
                val firstDone =
                    info.subjectCollectionInfo.episodes.firstOrNull { it.collectionType == UnifiedCollectionType.DONE }
                        ?.episodeInfo?.sort
                if (firstEp != null && firstDone != null) {
                    firstDone.compareTo(firstEp)
                } else {
                    Int.MIN_VALUE
                }
            }

    }
}

