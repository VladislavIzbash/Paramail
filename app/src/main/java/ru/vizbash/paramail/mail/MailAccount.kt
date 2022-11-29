package ru.vizbash.paramail.mail

import java.util.Properties

data class Creds(
    val login: String,
    val secret: String,
)

data class MailData(
    val host: String,
    val port: Int,
    val creds: Creds?,
)

data class MailAccount(
    val props: Properties,
    val smtp: MailData,
    val imap: MailData,
)