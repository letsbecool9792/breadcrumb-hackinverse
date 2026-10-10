package com.lbc.breadcrumb.data

import androidx.room.TypeConverter

/**
 * Enums are stored by name rather than ordinal, so reordering or inserting a
 * constant cannot silently reinterpret existing rows.
 */
class Converters {
    @TypeConverter
    fun memoryTypeToString(value: MemoryType): String = value.name

    @TypeConverter
    fun stringToMemoryType(value: String): MemoryType = MemoryType.valueOf(value)

    @TypeConverter
    fun syncStateToString(value: SyncState): String = value.name

    @TypeConverter
    fun stringToSyncState(value: String): SyncState = SyncState.valueOf(value)
}
