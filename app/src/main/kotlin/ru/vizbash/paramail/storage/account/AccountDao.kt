package ru.vizbash.paramail.storage.account

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts")
    fun getAll(): Flow<List<MailAccount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: MailAccount): Long

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

    @Transaction
    suspend fun getOrInsertFolder(name: String, accountId: Int): Long {
        val folder = getFolderByName(name, accountId)
        return if (folder != null) {
            folder.id.toLong()
        } else {
            val entity = FolderEntity(0, accountId, name)
            insertFolders(listOf(entity)).first()
        }
    }
}