package ru.vizbash.paramail.storage.message

import androidx.room.*
import ru.vizbash.paramail.storage.account.FolderEntity
import ru.vizbash.paramail.storage.account.MailAccount
import java.util.Date

@Entity(
    tableName = "messages",
    indices = [
        Index("msgnum"),
        Index("account_id"),
        Index("folder_id"),
        Index("from_id"),
        Index("to_id"),
  ],
    foreignKeys = [
        ForeignKey(
            entity = MailAccount::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folder_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Address::class,
            parentColumns = ["id"],
            childColumns = ["from_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = Address::class,
            parentColumns = ["id"],
            childColumns = ["to_id"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
)
data class Message(
    @ColumnInfo(name = "id") @PrimaryKey(autoGenerate = true) val id: Int,
    @ColumnInfo(name = "account_id") val accountId: Int,
    @ColumnInfo(name = "folder_id") val folderId: Int,
    @ColumnInfo(name = "msgnum") val msgNum: Int,
    @ColumnInfo(name = "from_id") val fromId: Int,
    @ColumnInfo(name = "to_id") val toId: Int,
    @ColumnInfo(name = "subject") val subject: String,
    @ColumnInfo(name = "timestamp") val date: Date,
    @ColumnInfo(name = "is_unread") val isUnread: Boolean,
)

@Entity(
    tableName = "addresses",
    indices = [Index("address", unique = true)],
)
data class Address(
    @ColumnInfo(name = "id") @PrimaryKey(autoGenerate = true) val id: Int,
    @ColumnInfo(name = "address") val address: String,
    @ColumnInfo(name = "personal") val personal: String?,
) {
    override fun toString(): String {
        return if (personal.isNullOrEmpty()) {
            address
        } else {
            "$personal <$address>"
        }
    }
}

@Entity(
    tableName = "cc_recipients",
    primaryKeys = ["msg_id", "address_id"],
    indices = [Index("msg_id"), Index("address_id")],
    foreignKeys = [
        ForeignKey(
            entity = Message::class,
            parentColumns = ["id"],
            childColumns = ["msg_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Address::class,
            parentColumns = ["id"],
            childColumns = ["address_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class CcRecipient(
    @ColumnInfo(name = "msg_id") val msgId: Int,
    @ColumnInfo(name = "address_id") val addressId: Int,
)

data class MessageWithRecipients(
    @Embedded val msg: Message,
    @Relation(
        parentColumn = "from_id",
        entityColumn = "id",
    )
    val from: Address,
    @Relation(
        parentColumn = "to_id",
        entityColumn = "id",
    )
    val to: Address,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            CcRecipient::class,
            parentColumn = "msg_id",
            entityColumn = "address_id",
        ),
    )
    val cc: List<Address>,
)
