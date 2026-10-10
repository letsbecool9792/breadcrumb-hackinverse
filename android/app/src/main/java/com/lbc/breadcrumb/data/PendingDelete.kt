package com.lbc.breadcrumb.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A memory deleted on the phone whose delete the server has not heard yet.
 * The row itself is gone the moment it is deleted; this is what remains, so
 * the upload queue can send the delete whenever there is a network -- a delete
 * that stayed on the phone would leave the text and its vector in the cloud.
 */
@Entity(tableName = "pending_deletes")
data class PendingDelete(
    @PrimaryKey val id: String,
    val requestedAt: Long,
)
