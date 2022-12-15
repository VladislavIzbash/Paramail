package ru.vizbash.paramail.storage

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import ru.vizbash.paramail.storage.entity.MailAccount
import ru.vizbash.paramail.storage.entity.Message

@Database(
    entities = [MailAccount::class, Message::class],
    version = 4,
)
@TypeConverters(Converters::class)
abstract class MailDatabase: RoomDatabase() {
    abstract fun accountDao(): AccountDao

    abstract fun messageDao(): MessageDao
}