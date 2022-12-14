package ru.vizbash.paramail.storage.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

data class Creds(
    @ColumnInfo(name = "login") val login: String,
    @ColumnInfo(name = "secret") val secret: String,
)

data class MailData(
    @ColumnInfo(name = "host") val host: String,
    @ColumnInfo(name = "port") val port: Int,
    @Embedded val creds: Creds?,
)

@Entity(tableName = "accounts")
data class MailAccount(
    @ColumnInfo(name = "id") @PrimaryKey(autoGenerate = true) val id: Int,
    @ColumnInfo(name = "properties") val props: Properties,
    @Embedded(prefix = "stmp_") val smtp: MailData,
    @Embedded(prefix = "imap_") val imap: MailData,
)
