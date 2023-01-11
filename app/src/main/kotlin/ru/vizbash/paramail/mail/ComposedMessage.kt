package ru.vizbash.paramail.mail

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

enum class MessageType { DEFAULT, REPLY, REPLY_TO_ALL, FORWARD }

@Parcelize
data class ComposedMessage(
    val subject: String = "",
    val to: String = "",
    val cc: Set<String> = setOf(),
    val attachments: List<Pair<Uri, String>> = listOf(),
    val text: String = "",
    val type: MessageType = MessageType.DEFAULT,
    val origMsgNum: Int? = null,
    val origMsgFolder: String? = null,
) : Parcelable