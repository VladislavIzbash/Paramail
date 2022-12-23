package ru.vizbash.paramail.storage.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = MessageBody::class,
            parentColumns = ["id"],
            childColumns = ["body_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class Message(
    @ColumnInfo(name = "id") @PrimaryKey(autoGenerate = true) val id: Int,
    @ColumnInfo(name = "msgnum") val msgnum: Int,
    @ColumnInfo(name = "account_id") val accountId: Int,
    @ColumnInfo(name = "subject") val subject: String,
    @ColumnInfo(name = "recipients") val recipients: List<String>,
    @ColumnInfo(name = "from") val from: String,
    @ColumnInfo(name = "body_id") val body_id: Int?,
    @ColumnInfo(name = "timestamp") val date: Date,
    @ColumnInfo(name = "isUnread") val isUnread: Boolean,
)
