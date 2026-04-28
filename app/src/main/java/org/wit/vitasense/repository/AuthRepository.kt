package org.wit.vitasense.repository

import kotlinx.coroutines.flow.Flow
import org.wit.vitasense.model.AuthResult
import org.wit.vitasense.model.AuthUser

interface AuthRepository {
    fun observeCurrentUser(): Flow<AuthUser?>

    suspend fun getCurrentUser(): AuthUser?

    suspend fun register(
        fullName: String,
        email: String,
        username: String,
        password: String,
        birthDate: String,
    ): AuthResult

    suspend fun login(
        identifier: String,
        password: String,
    ): AuthResult

    suspend fun logout()
}
