package ru.vizbash.paramail.storage

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import ru.vizbash.paramail.storage.entity.MailAccount
import ru.vizbash.paramail.storage.entity.Message
import ru.vizbash.paramail.storage.entity.MessagePart

@Database(
    entities = [MailAccount::class, Message::class, MessagePart::class],
    version = 5,
)
@TypeConverters(Converters::class)
abstract class MailDatabase: RoomDatabase() {
    abstract fun accountDao(): AccountDao

    abstract fun messageDao(): MessageDao
}