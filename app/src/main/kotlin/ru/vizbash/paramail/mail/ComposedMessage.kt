package ru.vizbash.paramail.mail

import android.net.Uri
import ru.vizbash.paramail.storage.message.Message

enum class MessageType { DEFAULT, REPLY, REPLY_TO_ALL, FORWARD }

data class ComposedMessage(
    val subject: String,
    val to: String,
    val cc: List<String>,
    val attachments: List<Pair<Uri, String>>,
    val text: String,
    val type: MessageType,
    val origMsg: Message?,
)