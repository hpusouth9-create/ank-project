package com.p2pvoice.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_logs")
data class CallLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val peerId: String,
    val timestamp: Long,
    val duration: Long = 0,
    val isIncoming: Boolean,
    val callType: String = "AUDIO"
)
