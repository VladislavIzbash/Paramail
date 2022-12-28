package ru.vizbash.paramail.storage.message

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "messages",
    indices = [
        Index("msgnum", unique = true),
    ],
)
data class Message(
    @ColumnInfo(name = "id") @PrimaryKey(autoGenerate = true) val id: Int,
    @ColumnInfo(name = "msgnum") val msgNum: Int,
    @ColumnInfo(name = "account_id") val accountId: Int,
    @ColumnInfo(name = "subject") val subject: String,
    @ColumnInfo(name = "recipients") val recipients: List<String>,
    @ColumnInfo(name = "from") val from: String,
    @ColumnInfo(name = "timestamp") val date: Date,
    @ColumnInfo(name = "isUnread") val isUnread: Boolean,
)
