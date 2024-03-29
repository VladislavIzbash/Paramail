package ru.vizbash.paramail.storage.message

import androidx.paging.PagingSource
import androidx.room.*
import kotlinx.coroutines.flow.Flow

private const val MESSAGE_SELECT = "SELECT * FROM messages " +
        "WHERE account_id = :accountId AND folder_id = :folderId"

@Dao
interface MessageDao {

    @Transaction
    @Query("$MESSAGE_SELECT ORDER BY msgnum DESC")
    fun getAllPaged(accountId: Int, folderId: Int): PagingSource<Int, MessageWithRecipients>

    @Query("$MESSAGE_SELECT ORDER BY msgnum DESC LIMIT 1")
    suspend fun getMostRecent(accountId: Int, folderId: Int): Message?

    @Query("$MESSAGE_SELECT ORDER BY msgnum ASC LIMIT 1")
    suspend fun getOldest(accountId: Int, folderId: Int): Message?

    @Transaction
    @Query("$MESSAGE_SELECT AND msgnum BETWEEN :startNum AND :endNum ORDER BY msgnum")
    suspend fun getInRange(accountId: Int, folderId: Int, startNum: Int, endNum: Int): List<MessageWithRecipients>

    @Transaction
    @Query("SELECT m.* FROM messages m " +
            "JOIN addresses a ON m.from_id = a.id " +
            "WHERE account_id = :accountId AND folder_id = :folderId " +
            "AND (LOWER(subject) LIKE LOWER(:pattern) OR LOWER(a.address || a.personal) LIKE LOWER(:pattern)) " +
            "ORDER BY msgnum DESC")
    fun searchEnvelopes(accountId: Int, folderId: Int, pattern: String): Flow<List<MessageWithRecipients>>

    @Delete
    suspend fun delete(message: Message)

    @Update
    suspend fun update(message: Message)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: Message): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAddressUnchecked(address: Address): Long

    @Query("SELECT * FROM addresses WHERE address = :address")
    suspend fun getAddress(address: String): Address?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCcRecipients(recipients: List<CcRecipient>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBody(body: MessageBody)

    @Query("SELECT * FROM message_bodies WHERE msg_id = :messageId")
    suspend fun getMessageBody(messageId: Int): MessageBody?

    @Insert
    suspend fun insertAttachments(attachments: List<Attachment>)

    @Query("SELECT * FROM attachments WHERE msg_id = :messageId")
    suspend fun getAttachments(messageId: Int): List<Attachment>

    @Transaction
    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: Int): MessageWithRecipients?

    @Query("SELECT * FROM messages WHERE folder_id = :folderId AND msgnum = :num")
    suspend fun getByMsgNum(folderId: Int, num: Int): Message?

    @Query("SELECT DISTINCT address FROM addresses " +
            "WHERE LOWER(address) LIKE LOWER(:pattern) " +
            "LIMIT :limit")
    suspend fun searchAddresses(pattern: String, limit: Int): List<String>

    @Transaction
    suspend fun insertAddress(address: Address): Long {
        val stored = getAddress(address.address)
        return stored?.id?.toLong() ?: insertAddressUnchecked(address)
    }

    @Transaction
    suspend fun insertMessagesWithRecipients(messages: List<MessageWithRecipients>) {
        for (message in messages) {
            if (getByMsgNum(message.msg.folderId, message.msg.msgNum) != null) {
                continue
            }

            val fromId = insertAddress(message.from)
            val toId = insertAddress(message.to)
            val msgId = insert(message.msg.copy(
                fromId = fromId.toInt(),
                toId = toId.toInt(),
            ))

            val ccIds = message.cc.map { insertAddress(it) }
            val cc = ccIds.map { CcRecipient(msgId.toInt(), it.toInt()) }
            insertCcRecipients(cc)
        }
    }
}