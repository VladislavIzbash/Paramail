package ru.vizbash.paramail.storage.message

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "message_bodies",
    foreignKeys = [
        ForeignKey(
            entity = Message::class,
            parentColumns = ["id"],
            childColumns = ["msg_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class MessageBody(
    @ColumnInfo(name = "msg_id") @PrimaryKey(autoGenerate = false) val msgId: Int,
    @ColumnInfo(name = "content") val content: ByteArray,
    @ColumnInfo(name = "mime") val mime: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MessageBody

        if (msgId != other.msgId) return false
        if (mime != other.mime) return false

        return true
    }

    override fun hashCode(): Int {
        var result = msgId
        result = 31 * result + mime.hashCode()
        return result
    }
}