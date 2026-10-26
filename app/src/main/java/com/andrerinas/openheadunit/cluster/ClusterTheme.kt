package com.andrerinas.openheadunit.cluster

import android.graphics.Color

/**
 * Visual themes for the instrument cluster (Secondary Display).
 * Inspired by modern smart cockpit UX with multi-style instrument dials.
 */
enum class ClusterTheme(
    val id: String,
    val displayName: String,
    val primaryAccent: Int,
    val secondaryAccent: Int,
    val backgroundStart: Int,
    val backgroundEnd: Int,
    val cardTint: Int,
    val speedColor: Int,
) {
    CYBER_BLUE(
        id = "cyber_blue",
        displayName = "科技深蓝",
        primaryAccent = Color.rgb(10, 132, 255),
        secondaryAccent = Color.rgb(0, 229, 255),
        backgroundStart = Color.rgb(13, 27, 42),
        backgroundEnd = Color.rgb(5, 8, 17),
        cardTint = Color.rgb(10, 132, 255),
        speedColor = Color.rgb(0, 229, 255),
    ),
    SPORT_RED(
        id = "sport_red",
        displayName = "赛道激情红",
        primaryAccent = Color.rgb(255, 45, 85),
        secondaryAccent = Color.rgb(255, 149, 0),
        backgroundStart = Color.rgb(36, 7, 13),
        backgroundEnd = Color.rgb(11, 1, 3),
        cardTint = Color.rgb(255, 45, 85),
        speedColor = Color.rgb(255, 59, 48),
    ),
    EXECUTIVE_GOLD(
        id = "executive_gold",
        displayName = "雅致黑金",
        primaryAccent = Color.rgb(212, 175, 55),
        secondaryAccent = Color.rgb(255, 213, 79),
        backgroundStart = Color.rgb(28, 24, 16),
        backgroundEnd = Color.rgb(8, 7, 5),
        cardTint = Color.rgb(212, 175, 55),
        speedColor = Color.rgb(255, 213, 79),
    ),
    AURORA_GREEN(
        id = "aurora_green",
        displayName = "极光翡翠",
        primaryAccent = Color.rgb(0, 230, 118),
        secondaryAccent = Color.rgb(105, 240, 174),
        backgroundStart = Color.rgb(7, 31, 24),
        backgroundEnd = Color.rgb(2, 11, 8),
        cardTint = Color.rgb(0, 230, 118),
        speedColor = Color.rgb(0, 230, 118),
    );

    companion object {
        fun fromId(id: String?): ClusterTheme =
            values().firstOrNull { it.id.equals(id, ignoreCase = true) } ?: CYBER_BLUE
    }
}
