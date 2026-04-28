package org.wit.vitasense.ui.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test
import org.wit.vitasense.model.AuthResult
import org.wit.vitasense.model.AuthUser
import org.wit.vitasense.repository.AuthRepository

class AuthViewModelTest {
    @Test
    fun validates_register_form_before_repository_call() = runBlocking {
        val repository = FakeAuthRepository()
        val viewModel =
            AuthViewModel(
                repository,
                CoroutineScope(Job() + Dispatchers.Unconfined),
                Dispatchers.Unconfined,
            )

        viewModel.submitRegister(
            fullName = "",
            email = "not-an-email",
            username = "",
            password = "123",
            confirmPassword = "456",
            birthDate = "",
        )

        assertEquals("Passwords do not match.", viewModel.state.value.errorMessage)
    }

    @Test
    fun successful_register_updates_signed_in_state() = runBlocking {
        val user = AuthUser(1, "Ava Stone", "ava@example.com", "ava", "2000-01-02")
        val repository = FakeAuthRepository(registerResult = AuthResult.Success(user))
        val viewModel =
            AuthViewModel(
                repository,
                CoroutineScope(Job() + Dispatchers.Unconfined),
                Dispatchers.Unconfined,
            )

        viewModel.submitRegister(
            fullName = "Ava Stone",
            email = "ava@example.com",
            username = "ava",
            password = "password123",
            confirmPassword = "password123",
            birthDate = "2000-01-02",
        )
        yield()

        assertEquals("Ava Stone", viewModel.state.value.signedInUser?.fullName)
        assertEquals(null, viewModel.state.value.errorMessage)
    }

    @Test
    fun rejects_password_shorter_than_seven_characters() = runBlocking {
        val viewModel =
            AuthViewModel(
                FakeAuthRepository(),
                CoroutineScope(Job() + Dispatchers.Unconfined),
                Dispatchers.Unconfined,
            )

        viewModel.submitRegister(
            fullName = "Ava Stone",
            email = "ava@example.com",
            username = "ava",
            password = "abc123",
            confirmPassword = "abc123",
            birthDate = "2000-01-02",
        )

        assertEquals(
            "Password must be longer than 6 characters and contain letters and numbers.",
            viewModel.state.value.errorMessage,
        )
    }

    @Test
    fun rejects_password_without_digit() = runBlocking {
        val viewModel =
            AuthViewModel(
                FakeAuthRepository(),
                CoroutineScope(Job() + Dispatchers.Unconfined),
                Dispatchers.Unconfined,
            )

        viewModel.submitRegister(
            fullName = "Ava Stone",
            email = "ava@example.com",
            username = "ava",
            password = "abcdefg",
            confirmPassword = "abcdefg",
            birthDate = "2000-01-02",
        )

        assertEquals(
            "Password must be longer than 6 characters and contain letters and numbers.",
            viewModel.state.value.errorMessage,
        )
    }

    @Test
    fun rejects_password_without_letter() = runBlocking {
        val viewModel =
            AuthViewModel(
                FakeAuthRepository(),
                CoroutineScope(Job() + Dispatchers.Unconfined),
                Dispatchers.Unconfined,
            )

        viewModel.submitRegister(
            fullName = "Ava Stone",
            email = "ava@example.com",
            username = "ava",
            password = "1234567",
            confirmPassword = "1234567",
            birthDate = "2000-01-02",
        )

        assertEquals(
            "Password must be longer than 6 characters and contain letters and numbers.",
            viewModel.state.value.errorMessage,
        )
    }

    @Test
    fun preserves_invalid_credentials_error_from_login() = runBlocking {
        val viewModel =
            AuthViewModel(
                FakeAuthRepository(loginResult = AuthResult.Error("Invalid credentials.")),
                CoroutineScope(Job() + Dispatchers.Unconfined),
                Dispatchers.Unconfined,
            )

        viewModel.submitLogin("ava", "wrong-password")
        yield()

        assertEquals("Invalid credentials.", viewModel.state.value.errorMessage)
    }
}

private class FakeAuthRepository(
    private val registerResult: AuthResult = AuthResult.Error("unused"),
    private val loginResult: AuthResult = AuthResult.Error("unused"),
    private val currentUserFlow: Flow<AuthUser?> = flowOf(null),
) : AuthRepository {
    override fun observeCurrentUser(): Flow<AuthUser?> = currentUserFlow

    override suspend fun getCurrentUser(): AuthUser? = null

    override suspend fun register(
        fullName: String,
        email: String,
        username: String,
        password: String,
        birthDate: String,
    ): AuthResult = registerResult

    override suspend fun login(
        identifier: String,
        password: String,
    ): AuthResult = loginResult

    override suspend fun logout() = Unit
}
