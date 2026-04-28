package org.wit.vitasense.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.wit.vitasense.model.AuthResult
import org.wit.vitasense.model.AuthUser
import org.wit.vitasense.repository.AuthRepository

enum class AuthMode {
    LOGIN,
    REGISTER,
}

data class AuthScreenState(
    val mode: AuthMode = AuthMode.LOGIN,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val signedInUser: AuthUser? = null,
)

class AuthViewModel(
    private val authRepository: AuthRepository,
    scope: CoroutineScope? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val modelScope = scope ?: viewModelScope
    private val _state = MutableStateFlow(AuthScreenState())
    val state: StateFlow<AuthScreenState> = _state.asStateFlow()

    fun showLogin() {
        _state.value = _state.value.copy(mode = AuthMode.LOGIN, errorMessage = null)
    }

    fun showRegister() {
        _state.value = _state.value.copy(mode = AuthMode.REGISTER, errorMessage = null)
    }

    fun submitLogin(
        identifier: String,
        password: String,
    ) {
        if (identifier.isBlank() || password.isBlank()) {
            _state.value = _state.value.copy(errorMessage = "Identifier and password are required.")
            return
        }

        modelScope.launch {
            _state.value = _state.value.copy(isSubmitting = true, errorMessage = null)
            when (val result = withContext(ioDispatcher) { authRepository.login(identifier, password) }) {
                is AuthResult.Success ->
                    _state.value =
                        _state.value.copy(
                            isSubmitting = false,
                            signedInUser = result.user,
                            errorMessage = null,
                        )

                is AuthResult.Error ->
                    _state.value =
                        _state.value.copy(
                            isSubmitting = false,
                            errorMessage = result.message,
                        )
            }
        }
    }

    fun submitRegister(
        fullName: String,
        email: String,
        username: String,
        password: String,
        confirmPassword: String,
        birthDate: String,
    ) {
        if (password != confirmPassword) {
            _state.value = _state.value.copy(errorMessage = "Passwords do not match.")
            return
        }
        if (fullName.isBlank() || username.isBlank() || birthDate.isBlank()) {
            _state.value = _state.value.copy(errorMessage = "All fields are required.")
            return
        }
        if (!EMAIL_REGEX.matches(email.trim())) {
            _state.value = _state.value.copy(errorMessage = "Enter a valid email address.")
            return
        }
        if (password.length <= 6 || !password.any(Char::isLetter) || !password.any(Char::isDigit)) {
            _state.value =
                _state.value.copy(
                    errorMessage = "Password must be longer than 6 characters and contain letters and numbers.",
                )
            return
        }

        modelScope.launch {
            _state.value = _state.value.copy(isSubmitting = true, errorMessage = null)
            when (
                val result =
                    withContext(ioDispatcher) {
                        authRepository.register(
                            fullName = fullName,
                            email = email,
                            username = username,
                            password = password,
                            birthDate = birthDate,
                        )
                    }
            ) {
                is AuthResult.Success ->
                    _state.value =
                        _state.value.copy(
                            isSubmitting = false,
                            signedInUser = result.user,
                            errorMessage = null,
                        )

                is AuthResult.Error ->
                    _state.value =
                        _state.value.copy(
                            isSubmitting = false,
                            errorMessage = result.message,
                        )
            }
        }
    }

    private companion object {
        val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")
    }
}
