package org.wit.vitasense.model

sealed interface UiEvent {
    data class Message(
        val text: String,
    ) : UiEvent
}
