package ru.vizbash.paramail.storage.message

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface MessageDao {
    @Query(
        "SELECT * FROM messages " +
        "WHERE account_id = :accountId AND folder_id = :folderId " +
        "ORDER BY msgnum DESC"
    )
    fun getAllPaged(accountId: Int, folderId: Int): PagingSource<Int, Message>

    @Query("DELETE FROM messages")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(messages: List<Message>): List<Long>

    @Update
    suspend fun update(message: Message)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBody(body: MessageBody)

    @Query("SELECT * FROM message_bodies WHERE msg_id = :messageId")
    suspend fun getMessageBody(messageId: Int): MessageBody?

    @Insert
    suspend fun insertAttachments(attachments: List<Attachment>)

    @Query("SELECT * FROM attachments WHERE msg_id = :messageId")
    suspend fun getAttachments(messageId: Int): List<Attachment>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: Int): Message?

    @Query("SELECT * FROM messages WHERE msgnum = :num")
    suspend fun getByMsgNum(num: Int): Message?

    @Query("SELECT COUNT(*) from messages")
    suspend fun getMessageCount(): Int
}