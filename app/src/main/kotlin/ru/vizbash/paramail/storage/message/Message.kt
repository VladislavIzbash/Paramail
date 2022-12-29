package ru.vizbash.paramail.storage.message

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import ru.vizbash.paramail.storage.account.FolderEntity
import ru.vizbash.paramail.storage.account.MailAccount
import java.util.Date

@Entity(
    tableName = "messages",
    indices = [
        Index("msgnum", unique = true),
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
    ],
)
data class Message(
    @ColumnInfo(name = "id") @PrimaryKey(autoGenerate = true) val id: Int,
    @ColumnInfo(name = "msgnum") val msgNum: Int,
    @ColumnInfo(name = "account_id") val accountId: Int,
    @ColumnInfo(name = "folder_id") val folderId: Int,
    @ColumnInfo(name = "subject") val subject: String,
    @ColumnInfo(name = "recipients") val recipients: List<String>,
    @ColumnInfo(name = "from") val from: String,
    @ColumnInfo(name = "timestamp") val date: Date,
    @ColumnInfo(name = "isUnread") val isUnread: Boolean,
)
