package ru.vizbash.paramail.storage.account

import androidx.room.*

@Entity(
    tableName = "folders",
    indices = [Index("account_id")],
    foreignKeys = [
        ForeignKey(
            entity = MailAccount::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class FolderEntity(
    @ColumnInfo(name = "id") @PrimaryKey(autoGenerate = true) val id: Int,
    @ColumnInfo(name = "account_id") val accountId: Int,
    @ColumnInfo(name = "name") val name: String,
)
