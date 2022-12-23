package ru.vizbash.paramail.storage.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "message_bodies")
data class MessageBody(
    @ColumnInfo(name = "id") @PrimaryKey(autoGenerate = true) val id: Int,
    @ColumnInfo(name = "content") val content: String,
    @ColumnInfo(name = "mime") val mime: String,
)
