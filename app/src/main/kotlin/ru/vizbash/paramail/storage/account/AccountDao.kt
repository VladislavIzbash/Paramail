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

    @Query("SELECT name FROM folders WHERE account_id = :accountId")
    suspend fun getFolders(accountId: Int): List<String>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getFolderById(id: Int): FolderEntity?

    @Query("SELECT * FROM folders WHERE name = :name AND account_id = :accountId")
    suspend fun getFolderByName(name: String, accountId: Int): FolderEntity?

    @Insert
    suspend fun insertFolders(folders: List<FolderEntity>): List<Long>

    suspend fun getFolderOrInsert(name: String, accountId: Int): FolderEntity {
        val storedFolder = getFolderByName(name, accountId)

        return if (storedFolder != null) {
            storedFolder
        } else {
            val id = insertFolders(listOf(FolderEntity(0, accountId, name))).first()
            FolderEntity(id.toInt(), accountId, name)
        }
    }
}