package ru.vizbash.paramail.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ru.vizbash.paramail.storage.entity.MailAccount

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts")
    suspend fun getAll(): List<MailAccount>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: MailAccount)

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Int): MailAccount?
}