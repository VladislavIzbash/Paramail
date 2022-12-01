package ru.vizbash.paramail.storage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import ru.vizbash.paramail.mail.MailData
import java.util.*

@Entity(tableName = "account_entity")
data class AccountEntity(
    @PrimaryKey val id: Int,
    @ColumnInfo(name = "properties") val props: Properties,
    @ColumnInfo(name = "smtp_host") val smtpHost: String,
    @ColumnInfo(name = "smtp_port") val smtpPort: Int,
    @ColumnInfo(name = "smtp_login") val smtpLogin: String?,
    @ColumnInfo(name = "smtp_secret") val smtpSecret: String?,
    @ColumnInfo(name = "imap_host") val imapHost: String,
    @ColumnInfo(name = "imap_port") val imapPort: Int,
    @ColumnInfo(name = "imap_login") val imapLogin: String,
    @ColumnInfo(name = "imap_secret") val imapSecret: String,
)