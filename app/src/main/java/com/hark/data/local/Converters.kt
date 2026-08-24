package com.hark.data.local

import androidx.room.TypeConverter
import java.time.Instant

/** Room type converters. java.time is available directly on minSdk 26+. */
class Converters {
    @TypeConverter
    fun instantToEpochMilli(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun epochMilliToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun sourceToName(value: Source): String = value.name

    @TypeConverter
    fun nameToSource(value: String): Source = Source.valueOf(value)
}
