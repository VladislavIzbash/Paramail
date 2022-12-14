package ru.vizbash.paramail.storage

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ru.vizbash.paramail.storage.entity.Message

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE account_id = :accountId")
    fun pageAll(accountId: Int): PagingSource<Int, Message>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(messages: List<Message>)
}