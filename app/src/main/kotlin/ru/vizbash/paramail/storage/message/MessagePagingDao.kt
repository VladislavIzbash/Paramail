package ru.vizbash.paramail.storage.message

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface MessagePagingDao {
    @Query("SELECT * FROM message_paging_keys WHERE msg_id = :messageId")
    suspend fun getPagingKeyById(messageId: Int): MessagePagingKey?

    @Insert
    suspend fun insertPagingKeys(key: List<MessagePagingKey>)

    @Query("DELETE FROM message_paging_keys")
    suspend fun clearPagingKeys()
}