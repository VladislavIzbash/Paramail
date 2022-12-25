package ru.vizbash.paramail.storage.message

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "message_paging_keys")
data class MessagePagingKey(
    @PrimaryKey(autoGenerate = false) @ColumnInfo(name = "msg_id") val msg_id: Int,
    @ColumnInfo(name = "next_page") val nextPage: Int,
)