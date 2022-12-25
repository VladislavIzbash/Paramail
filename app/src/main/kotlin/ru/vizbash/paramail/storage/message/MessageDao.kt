package ru.vizbash.paramail.storage.message

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE account_id = :accountId ORDER BY msgnum DESC")
    fun pageAll(accountId: Int): PagingSource<Int, Message>

    @Query("DELETE FROM messages")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<Message>): List<Long>

    @Update
    suspend fun update(message: Message)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBodyParts(parts: List<MessagePart>)

    @Query("SELECT * FROM message_parts WHERE message_id = :messageId")
    suspend fun getBodyParts(messageId: Int): List<MessagePart>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: Int): Message?

    @Query("SELECT COUNT(*) from messages")
    suspend fun getMessageCount(): Int
}