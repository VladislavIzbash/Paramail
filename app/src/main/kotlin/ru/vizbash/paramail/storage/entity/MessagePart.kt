package ru.vizbash.paramail.storage.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "message_parts",
    foreignKeys = [
        ForeignKey(
            entity = Message::class,
            parentColumns = ["id"],
            childColumns = ["message_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["message_id"]),
    ],
)
data class MessagePart(
    @ColumnInfo(name = "id") @PrimaryKey(autoGenerate = true) val id: Int,
    @ColumnInfo(name = "message_id") val messageId: Int,
    @ColumnInfo(name = "content") val content: ByteArray,
    @ColumnInfo(name = "mime") val mime: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MessagePart

        if (id != other.id) return false
        if (mime != other.mime) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + mime.hashCode()
        return result
    }
}
