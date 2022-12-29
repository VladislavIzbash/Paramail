package ru.vizbash.paramail.storage.message

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = Message::class,
            parentColumns = ["id"],
            childColumns = ["msg_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["msg_id"]),
    ],
)
data class Attachment(
    @ColumnInfo(name = "id") @PrimaryKey(autoGenerate = true) val id: Int,
    @ColumnInfo(name = "msg_id") val msgId: Int,
    @ColumnInfo(name = "filename") val fileName: String,
    @ColumnInfo(name = "mime") val mime: String,
    @ColumnInfo(name = "size") val size: Int,
)
