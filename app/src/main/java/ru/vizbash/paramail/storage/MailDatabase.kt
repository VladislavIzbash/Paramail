package ru.vizbash.paramail.storage

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [AccountEntity::class],
    version = 1,
)
@TypeConverters(Converters::class)
abstract class MailDatabase: RoomDatabase() {
    abstract fun accountDao(): AccountDao
}