package ru.vizbash.paramail.storage

import androidx.room.TypeConverter
import java.io.ByteArrayOutputStream
import java.util.*

class Converters {
    @TypeConverter
    fun bytesToProperties(bytes: ByteArray) = Properties().also {
        it.load(bytes.inputStream())
    }

    @TypeConverter
    fun propertiesToBytes(p: Properties): ByteArray {
        val output = ByteArrayOutputStream()
        p.store(output, null)
        return output.toByteArray()
    }

    @TypeConverter
    fun dateToLong(date: Date) = date.time

    @TypeConverter
    fun longToDate(ts: Long) = Date(ts)
}