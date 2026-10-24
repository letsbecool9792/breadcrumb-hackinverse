package com.lbc.breadcrumb.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A memory deleted on the phone whose delete the server had not heard yet,
 * for the upload queue to send. Nothing writes or reads it since local-first
 * L7, when memories stopped leaving the phone; the table stays so the schema
 * doesn't change.
 */
@Entity(tableName = "pending_deletes")
data class PendingDelete(
    @PrimaryKey val id: String,
    val requestedAt: Long,
)
