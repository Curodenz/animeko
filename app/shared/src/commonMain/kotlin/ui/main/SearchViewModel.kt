/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.main

import androidx.compose.runtime.Stable
import androidx.paging.cachedIn
import androidx.paging.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.data.repository.episode.EpisodeCollectionRepository
import me.him188.ani.app.data.repository.subject.BangumiSubjectSearchCompletionRepository
import me.him188.ani.app.data.repository.subject.SubjectSearchHistoryRepository
import me.him188.ani.app.data.repository.subject.SubjectSearchRepository
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.search.SubjectSearchQuery
import me.him188.ani.app.ui.exploration.search.SearchPageState
import me.him188.ani.app.ui.exploration.search.SubjectPreviewItemInfo
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.foundation.launchInBackground
import me.him188.ani.app.ui.search.PagingSearchState
import me.him188.ani.app.ui.subject.details.state.SubjectDetailsStateFactory
import me.him188.ani.app.ui.subject.details.state.SubjectDetailsStateLoader
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.milliseconds

@Stable
class SearchViewModel : AbstractViewModel(), KoinComponent {
    private val searchHistoryRepository: SubjectSearchHistoryRepository by inject()
    private val bangumiSubjectSearchCompletionRepository: BangumiSubjectSearchCompletionRepository by inject()

    private val episodeCollectionRepository: EpisodeCollectionRepository by inject()
    private val subjectSearchRepository: SubjectSearchRepository by inject()
    private val subjectDetailsStateFactory: SubjectDetailsStateFactory by inject()
    private val settingsRepository: SettingsRepository by inject()

    private val nsfwSettingFlow = settingsRepository.uiSettings.flow.map { it.searchSettings.nsfwMode }

    private val queryFlow = MutableStateFlow(SubjectSearchQuery(""))

    val searchPageState: SearchPageState = SearchPageState(
        searchHistoryPager = searchHistoryRepository.getHistoryPager(),
        suggestionsPager = queryFlow.debounce(200.milliseconds).flatMapLatest {
            bangumiSubjectSearchCompletionRepository.completionsFlow(it.keywords)
        },
        queryFlow = queryFlow.map { it.keywords },
        setQuery = { newQuery ->
            queryFlow.update { it.copy(keywords = newQuery) }
        },
        onRequestPlay = { info ->
            episodeCollectionRepository.subjectEpisodeCollectionInfosFlow(info.subjectId).first().firstOrNull()?.let {
                SearchPageState.EpisodeTarget(info.subjectId, it.episodeInfo.episodeId)
            }
        },
        searchState = PagingSearchState(
            createPager = {
                // 搜索总是会包含 NSFW
                val query = queryFlow.value
                subjectSearchRepository.searchSubjects(
                    query,
                    useNewApi = {
                        query.hasFilters() ||
                                settingsRepository.uiSettings.flow.map { it.searchSettings.enableNewSearchSubjectApi }
                                    .first()
                    },
                ).combine(nsfwSettingFlow) { data, nsfwMode ->
                    // 当 settings 变更时, 会重新计算所有的 SubjectPreviewItemInfo 以更新其显示状态, 但不会重新搜索.
                    data.map { subject ->
                        SubjectPreviewItemInfo.compute(
                            subject.subjectInfo,
                            subject.mainEpisodeCount,
                            nsfwMode,
                            subject.lightSubjectRelations.lightRelatedPersonInfoList,
                            subject.lightSubjectRelations.lightRelatedCharacterInfoList,
                        )
                    }
                    // 我们必须保证 data 的数量和 map 后的数量一致, 否则会导致 Pager 搜索下一页时使用的 offset 有误.
                }.cachedIn(backgroundScope)
            },
        ),
        onRemoveHistory = {
            searchHistoryRepository.removeHistory(it)
        },
        backgroundScope = backgroundScope,
        onStartSearch = { query ->
            subjectDetailsStateLoader.clear()
            launchInBackground {
                searchHistoryRepository.addHistory(query)
            }
        },
    )

    val subjectDetailsStateLoader = SubjectDetailsStateLoader(subjectDetailsStateFactory, backgroundScope)
    private var currentPreviewingSubject: SubjectInfo? = null

    fun viewSubjectDetails(previewItem: SubjectPreviewItemInfo) {
        subjectDetailsStateLoader.clear()
        subjectDetailsStateLoader.load(
            previewItem.subjectId,
            placeholder = SubjectInfo.createPlaceholder(
                previewItem.subjectId,
                previewItem.title,
                previewItem.imageUrl,
                previewItem.title,
            ).also { currentPreviewingSubject = it },
        )
    }

    fun reloadCurrentSubjectDetails() {
        val curr = currentPreviewingSubject ?: return
        subjectDetailsStateLoader.reload(curr.subjectId, curr)
    }

    override fun onCleared() {
        super.onCleared()
    }
}

