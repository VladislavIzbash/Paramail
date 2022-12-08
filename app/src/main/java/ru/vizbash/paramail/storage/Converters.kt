package ru.vizbash.paramail.storage

import androidx.room.TypeConverter
import java.io.ByteArrayOutputStream
import java.util.Properties

class Converters {
    @TypeConverter
    fun stringToProperties(s: String) = Properties().also {
        it.load(s.byteInputStream())
    }

    @TypeConverter
    fun propertiesToString(p: Properties): String {
        val output = ByteArrayOutputStream()
        p.store(output, "")
        return p.toString()
    }
}