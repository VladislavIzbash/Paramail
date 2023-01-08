package ru.vizbash.paramail.storage.account

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts")
    fun getAll(): Flow<List<MailAccount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: MailAccount)

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Int): MailAccount?

    @Query("SELECT * FROM folders WHERE account_id = :accountId")
    suspend fun getFolders(accountId: Int): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE name = :name AND account_id = :accountId")
    suspend fun getFolderByName(name: String, accountId: Int): FolderEntity?

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getFolderById(id: Int): FolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolders(folders: List<FolderEntity>): List<Long>
}