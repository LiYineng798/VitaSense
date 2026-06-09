package org.wit.vitasense.ui.aichat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.wit.vitasense.model.AiChatMessageStatus
import org.wit.vitasense.model.AiChatRole
import org.wit.vitasense.repository.AiChatRepository
import org.wit.vitasense.repository.SettingsRepository

@OptIn(ExperimentalCoroutinesApi::class)
class AiChatViewModel(
    private val aiChatRepository: AiChatRepository,
    private val settingsRepository: SettingsRepository,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val modelScope = scope ?: viewModelScope
    private val localError = MutableStateFlow<String?>(null)

    val state: StateFlow<AiChatScreenState> =
        combine(
            aiChatRepository.observeCurrentSession(),
            settingsRepository.observeAiProviderConfig(),
            localError,
        ) { session, config, error ->
            Triple(session, config, error)
        }.flatMapLatest { (session, config, error) ->
            val messagesFlow = session?.let { aiChatRepository.observeMessages(it.id) } ?: flowOf(emptyList())
            messagesFlow.combine(flowOf(Triple(session, config, error))) { messages, values ->
                val currentSession = values.first
                val currentConfig = values.second
                val generating =
                    messages.any {
                        it.status == AiChatMessageStatus.STREAMING.storageKey ||
                            it.status == AiChatMessageStatus.SENDING.storageKey
                    }
                AiChatScreenState(
                    sessionId = currentSession?.id,
                    title = currentSession?.title ?: "AI Chat",
                    messages =
                        messages.map {
                            AiChatMessageUiModel(
                                id = it.id,
                                role = it.role,
                                content = it.content,
                                isAssistant = it.role == AiChatRole.ASSISTANT.storageKey,
                                isStreaming = it.status == AiChatMessageStatus.STREAMING.storageKey,
                                errorText = it.errorMessage,
                            )
                        },
                    setupRequired = !currentConfig.isComplete,
                    isGenerating = generating,
                    errorText = values.third,
                )
            }
        }.stateIn(
            scope = modelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AiChatScreenState(),
        )

    fun sendMessage(text: String) {
        if (state.value.setupRequired || state.value.isGenerating) return
        modelScope.launch {
            localError.value = null
            aiChatRepository.sendMessage(text)
        }
    }

    fun startNewChat() {
        modelScope.launch {
            aiChatRepository.startNewChat()
        }
    }

    fun deleteCurrentChat() {
        modelScope.launch {
            aiChatRepository.deleteCurrentChat()
        }
    }
}
