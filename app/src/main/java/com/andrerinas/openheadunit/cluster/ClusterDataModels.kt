package com.andrerinas.openheadunit.cluster

import android.graphics.Bitmap

/**
 * Next-turn instruction guidance for instrument cluster presentation.
 */
data class ClusterTurnGuidance(
    val icon: Int = 1,
    val roundaboutExit: Int = 0,
    val distanceMeters: Int = 0,
    val road: String = "",
    val currentRoad: String = "",
    val remainingMeters: Long? = null,
    val remainingSeconds: Long? = null,
    val arrivalEpochSeconds: Long? = null,
    val actionText: String = "",
)

/**
 * Music / Media track info for the instrument cluster.
 */
data class ClusterSong(
    val title: String = "",
    val artist: String = "",
    val playing: Boolean = false,
    val artwork: Bitmap? = null,
)
