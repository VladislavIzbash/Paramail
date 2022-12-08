package ru.vizbash.paramail.storage

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
class StorageModule {
    @Provides
    fun provideMailDatabase(@ApplicationContext context: Context): MailDatabase {
        return Room.databaseBuilder(context, MailDatabase::class.java, "paramail").build()
    }

    @Provides
    fun provideAccountDao(db: MailDatabase): AccountDao = db.accountDao()
}