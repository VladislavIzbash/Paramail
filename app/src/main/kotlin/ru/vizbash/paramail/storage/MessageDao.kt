package ru.vizbash.paramail.storage

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import ru.vizbash.paramail.storage.entity.Message
import ru.vizbash.paramail.storage.entity.MessagePart

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE account_id = :accountId")
    fun pageAll(accountId: Int): PagingSource<Int, Message>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(messages: List<Message>)

    @Update
    suspend fun update(message: Message)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBodyParts(parts: List<MessagePart>)

    @Query("SELECT * FROM message_parts WHERE message_id = :messageId")
    suspend fun getBodyParts(messageId: Int): List<MessagePart>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: Int): Message?
}