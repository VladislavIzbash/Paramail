package ru.vizbash.paramail.storage

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import ru.vizbash.paramail.storage.account.AccountDao
import ru.vizbash.paramail.storage.account.FolderEntity
import ru.vizbash.paramail.storage.account.MailAccount
import ru.vizbash.paramail.storage.message.*

@Database(
    entities = [
        MailAccount::class,
        Message::class,
        MessageBody::class,
        Attachment::class,
        FolderEntity::class,
    ],
    version = 7,
)
@TypeConverters(Converters::class)
abstract class MailDatabase: RoomDatabase() {
    abstract fun accountDao(): AccountDao

    abstract fun messageDao(): MessageDao
}