package ru.vizbash.paramail.storage.message

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    companion object {
        private const val MESSAGE_SELECT = "SELECT * FROM messages " +
                "WHERE account_id = :accountId AND folder_id = :folderId "
    }

    @Query("$MESSAGE_SELECT ORDER BY msgnum DESC")
    fun getAllPaged(accountId: Int, folderId: Int): PagingSource<Int, Message>

    @Query("$MESSAGE_SELECT ORDER BY msgnum DESC LIMIT 1")
    suspend fun getMostRecent(accountId: Int, folderId: Int): Message?

    @Query("$MESSAGE_SELECT ORDER BY msgnum ASC LIMIT 1")
    suspend fun getOldest(accountId: Int, folderId: Int): Message?

    @Query("$MESSAGE_SELECT AND (LOWER(subject) LIKE LOWER(:pattern) OR LOWER(`from`) LIKE LOWER(:pattern)) " +
            "ORDER BY msgnum DESC")
    fun searchEnvelopes(accountId: Int, folderId: Int, pattern: String): Flow<List<Message>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(messages: List<Message>): List<Long>

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
}