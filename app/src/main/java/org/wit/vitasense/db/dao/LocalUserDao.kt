package org.wit.vitasense.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.wit.vitasense.db.entity.LocalUserEntity

@Dao
interface LocalUserDao {
    @Insert
    suspend fun insert(user: LocalUserEntity): Long

    @Query("SELECT * FROM local_users WHERE email = :email LIMIT 1")
    suspend fun findByEmail(email: String): LocalUserEntity?

    @Query("SELECT * FROM local_users WHERE username = :username LIMIT 1")
    suspend fun findByUsername(username: String): LocalUserEntity?

    @Query("SELECT * FROM local_users WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): LocalUserEntity?

    @Query("SELECT * FROM local_users WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<LocalUserEntity?>
}
