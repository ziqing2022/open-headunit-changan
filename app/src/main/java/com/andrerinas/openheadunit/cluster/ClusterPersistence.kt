package com.andrerinas.openheadunit.cluster

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistence helper for instrument cluster settings, layout coordinates, scales, and crop rect.
 */
object ClusterPersistence {
    private const val PREFS = "openheadunit_cluster_settings"

    const val DEFAULT_CLUSTER_TURN_CARD_X_PX = 400
    const val DEFAULT_CLUSTER_TURN_CARD_Y_PX = 40

    const val DEFAULT_CLUSTER_ETA_CARD_X_PX = 40
    const val DEFAULT_CLUSTER_ETA_CARD_Y_PX = 540

    const val DEFAULT_CLUSTER_SONG_CARD_X_PX = 1300
    const val DEFAULT_CLUSTER_SONG_CARD_Y_PX = 40

    const val CLUSTER_CARD_SCALE_MIN = 60
    const val CLUSTER_CARD_SCALE_MAX = 160
    const val CLUSTER_CARD_SCALE_DEFAULT = 100

    private const val KEY_CLUSTER_MIRROR_MODE = "cluster_mirror_mode"
    private const val KEY_CLUSTER_MIRROR_LEFT_CROP = "cluster_mirror_left_crop_px"

    private const val KEY_CLUSTER_CROP_LEFT = "cluster_crop_left"
    private const val KEY_CLUSTER_CROP_TOP = "cluster_crop_top"
    private const val KEY_CLUSTER_CROP_WIDTH = "cluster_crop_width"
    private const val KEY_CLUSTER_CROP_HEIGHT = "cluster_crop_height"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadClusterMirrorMode(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CLUSTER_MIRROR_MODE, true)

    fun saveClusterMirrorMode(context: Context, mirror: Boolean) {
        prefs(context).edit().putBoolean(KEY_CLUSTER_MIRROR_MODE, mirror).apply()
    }

    fun loadClusterMirrorLeftCropPx(context: Context): Int =
        prefs(context).getInt(KEY_CLUSTER_MIRROR_LEFT_CROP, 0).coerceIn(0, 500)

    fun saveClusterMirrorLeftCropPx(context: Context, px: Int) {
        prefs(context).edit().putInt(KEY_CLUSTER_MIRROR_LEFT_CROP, px.coerceIn(0, 500)).apply()
    }

    fun loadClusterCropLeftPercent(context: Context): Int =
        prefs(context).getInt(KEY_CLUSTER_CROP_LEFT, 0).coerceIn(-50, 150)

    fun loadClusterCropTopPercent(context: Context): Int =
        prefs(context).getInt(KEY_CLUSTER_CROP_TOP, 0).coerceIn(-50, 150)

    fun loadClusterCropWidthPercent(context: Context): Int =
        prefs(context).getInt(KEY_CLUSTER_CROP_WIDTH, 100).coerceIn(1, 200)

    fun loadClusterCropHeightPercent(context: Context): Int =
        prefs(context).getInt(KEY_CLUSTER_CROP_HEIGHT, 100).coerceIn(1, 200)

    fun saveClusterCrop(context: Context, left: Int, top: Int, width: Int, height: Int) {
        prefs(context).edit()
            .putInt(KEY_CLUSTER_CROP_LEFT, left.coerceIn(-50, 150))
            .putInt(KEY_CLUSTER_CROP_TOP, top.coerceIn(-50, 150))
            .putInt(KEY_CLUSTER_CROP_WIDTH, width.coerceIn(1, 200))
            .putInt(KEY_CLUSTER_CROP_HEIGHT, height.coerceIn(1, 200))
            .apply()
    }

    fun loadCardX(context: Context, card: String, defaultX: Int): Int =
        prefs(context).getInt("cluster_${card}_x", defaultX)

    fun loadCardY(context: Context, card: String, defaultY: Int): Int =
        prefs(context).getInt("cluster_${card}_y", defaultY)

    fun saveCardX(context: Context, card: String, x: Int) {
        prefs(context).edit().putInt("cluster_${card}_x", x).apply()
    }

    fun saveCardY(context: Context, card: String, y: Int) {
        prefs(context).edit().putInt("cluster_${card}_y", y).apply()
    }

    fun loadCardScalePercent(context: Context, card: String): Int =
        prefs(context).getInt("cluster_${card}_scale", CLUSTER_CARD_SCALE_DEFAULT)
            .coerceIn(CLUSTER_CARD_SCALE_MIN, CLUSTER_CARD_SCALE_MAX)

    fun saveCardScalePercent(context: Context, card: String, percent: Int) {
        prefs(context).edit()
            .putInt("cluster_${card}_scale", percent.coerceIn(CLUSTER_CARD_SCALE_MIN, CLUSTER_CARD_SCALE_MAX))
            .apply()
    }

    fun cardScale(context: Context, card: String): Float =
        loadCardScalePercent(context, card) / 100f

    fun loadCardVisible(context: Context, card: String, defaultVisible: Boolean = true): Boolean =
        prefs(context).getBoolean("cluster_${card}_visible", defaultVisible)

    fun saveCardVisible(context: Context, card: String, visible: Boolean) {
        prefs(context).edit().putBoolean("cluster_${card}_visible", visible).apply()
    }

    fun loadCardAlpha(context: Context, card: String, defaultAlpha: Int = 50): Int =
        prefs(context).getInt("cluster_${card}_alpha", defaultAlpha).coerceIn(0, 100)

    fun saveCardAlpha(context: Context, card: String, alpha: Int) {
        prefs(context).edit().putInt("cluster_${card}_alpha", alpha.coerceIn(0, 100)).apply()
    }
}
