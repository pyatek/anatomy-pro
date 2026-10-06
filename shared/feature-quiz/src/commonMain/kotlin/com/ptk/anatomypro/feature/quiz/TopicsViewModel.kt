package com.ptk.anatomypro.feature.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ptk.anatomypro.core.data.model.Entitlements
import com.ptk.anatomypro.core.data.model.QuizFormat
import com.ptk.anatomypro.core.data.model.QuizTopic
import com.ptk.anatomypro.core.data.repository.EntitlementRepository
import com.ptk.anatomypro.core.data.repository.QuizRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One cell of screen 08's grid.
 *
 * A [locked] topic is still a cell: the student sees what a subscription adds, and choosing
 * it leads to the paywall (design spec §29.2).
 */
data class TopicCell(val topic: QuizTopic, val locked: Boolean) {
    /** Nothing learned yet — drawn differently from a topic in progress (spec §9). */
    val untouched: Boolean get() = topic.mastery == 0f
}

data class TopicsUiState(
    val cells: List<TopicCell> = emptyList(),
    val format: QuizFormat = QuizFormat.NAME_THE_HIGHLIGHTED,
    val isLoading: Boolean = true,
    val failed: Boolean = false,
)

/**
 * Prototype screen 08: topic selection.
 *
 * [locale] is the interface locale, which topic titles are shown in. Structure names inside
 * a session follow the examination locale instead, and that is the session's business.
 */
class TopicsViewModel(
    private val quiz: QuizRepository,
    private val entitlements: EntitlementRepository,
    private val locale: String,
) : ViewModel() {

    private val _state = MutableStateFlow(TopicsUiState())
    val state: StateFlow<TopicsUiState> = _state.asStateFlow()

    private var topics: List<QuizTopic> = emptyList()
    private var held: Entitlements = Entitlements(subscribed = false, ownedSystems = emptySet())

    init {
        load()
        viewModelScope.launch {
            entitlements.entitlements.collect { current ->
                held = current
                publish()
            }
        }
    }

    fun onFormat(format: QuizFormat) = _state.update { it.copy(format = format) }

    fun onRetry() {
        if (_state.value.isLoading) return
        load()
    }

    private fun load() {
        _state.update { it.copy(isLoading = true, failed = false) }
        viewModelScope.launch {
            try {
                topics = quiz.topics(locale)
                _state.update { it.copy(isLoading = false) }
                publish()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(isLoading = false, failed = true) }
            }
        }
    }

    private fun publish() {
        _state.update { state -> state.copy(cells = topics.map { TopicCell(it, locked = !held.allows(it.system)) }) }
    }
}
