package com.andrerinas.openheadunit.cluster

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.IInterface
import android.os.Looper
import android.os.Parcel
import android.os.SystemClock
import android.util.Log
import com.andrerinas.openheadunit.utils.AppLog

/**
 * InCallClusterBridge for Changan vehicles (UNI-V, CS75, Feiyu OS, etc.).
 *
 * This performs the head-unit-level "open/close dashboard projection" handshake that
 * the stock Amap normally performs when navigation starts, allowing Open Headunit to project
 * to the Changan instrument cluster without requiring the stock Amap to run in background.
 */
object InCallClusterBridge {
    private const val TAG = "InCallClusterBridge"

    // Service targets
    private const val PKG_INCALL = "com.incall.interactive"
    private const val SERVICE_ACTION = "com.incall.double.INTERACTIVE_SERVICE"
    private const val SERVICE_CLASS = "com.incall.interactive.service.DoubleInteractiveService"
    private const val BINDER_TOKEN = "com.incall.serversdk.interactive.IDouInteractiveManager"
    private const val CALLBACK_TOKEN = "com.incall.serversdk.interactive.callback.INavigateCallback"

    // Broadcast extras
    private const val ACTION_AUTONAVI = "AUTONAVI_STANDARD_BROADCAST_SEND"
    private const val EXTRA_KEY_TYPE = "KEY_TYPE"
    private const val EXTRA_STATE = "EXTRA_STATE"
    private const val EXTRA_CROSS_MAP = "EXTRA_CROSS_MAP"
    private const val EXTRA_OPERATE = "EXTSCREEN_OPERATE_TYPE"
    private const val EXTRA_STATUS = "EXTSCREEN_STATUS_INFO"
    private const val EXTRA_WINDOW = "EXTSCREEN_WINDOW_INFO"
    private const val EXTRA_MODE = "EXTSCREEN_MODE_INFO"

    // Packages that receive the 10116/10117 ext-screen broadcasts
    private val EXTERNAL_SCREEN_PKGS = arrayOf(
        "com.incall.interactive",
        "com.iflytek.autofly.launcher",
        "com.iflytek.autofly.applet",
        "com.incall.naviadapter",
    )

    // CarProperty IDs
    private const val CAR_PROP_SOURCE = 557855845
    private const val CAR_PROP_MODE = 557855847

    // Timings
    private val OPEN_RETRY_DELAYS = longArrayOf(80, 180, 420, 900, 1600, 2400, 3600, 5200, 7600)
    private val HOLD_RETRY_DELAYS = longArrayOf(80, 180, 420)
    private val SOURCE_SWITCH_DELAYS = intArrayOf(250, 1200)
    private val STATE_LADDER = intArrayOf(49, 3, 50, 311, 2001, 15, 116, 319, 3025, 46, -1, 200, 417)
    private const val SERVICE_WAIT_FALLBACK_MS = 4_000L

    private const val PREFS_NAME = "InCallClusterBridge"
    private const val KEY_INCALL_PROJECTING = "incallProjecting"
    private const val KEY_EXT_SCREEN_RELEASED = "extScreenReleased"
    private const val KEY_EXT_SCREEN_RELEASED_BY_US = "extScreenReleasedByUs"
    private const val R0_ANSWER_WINDOW_MS = 2_000L
    private const val CALLBACK_CODE_STATE = 2
    private const val SILENT_CONFIRM_MS = 4_000L
    private const val CALLBACK_PROJECTING = 3

    private var contextRef: Context? = null
    private var bound = false
    private var binder: IBinder? = null
    private var callbackRegistered = false
    private var observeCallbackRequested = false
    private val lastCallbackValue = java.util.concurrent.ConcurrentHashMap<Int, Int>()

    @Volatile
    private var callbackHeard = false

    private fun incallReportsProjecting(): Boolean = lastCallbackValue[2] == CALLBACK_PROJECTING

    fun reportsProjecting(): Boolean = incallReportsProjecting() && !releasedByUs()

    fun heardFromIncall(): Boolean = callbackHeard

    private fun prefs(): SharedPreferences? =
        contextRef?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun persistedProjecting(): Boolean =
        prefs()?.getBoolean(KEY_INCALL_PROJECTING, false) ?: false

    private fun persistedReleased(): Boolean =
        prefs()?.getBoolean(KEY_EXT_SCREEN_RELEASED, false) ?: false

    private fun releasedByUs(): Boolean =
        prefs()?.getBoolean(KEY_EXT_SCREEN_RELEASED_BY_US, false) ?: false

    private fun setReleasedByUs(value: Boolean) {
        prefs()?.edit()?.putBoolean(KEY_EXT_SCREEN_RELEASED_BY_US, value)?.apply()
    }

    private var r0SentAtUptimeMs = 0L

    private fun withinR0AnswerWindow(): Boolean =
        r0SentAtUptimeMs > 0 && SystemClock.uptimeMillis() - r0SentAtUptimeMs <= R0_ANSWER_WINDOW_MS

    private fun persistProjecting(projecting: Boolean) {
        val editor = prefs()?.edit() ?: return
        editor.putBoolean(KEY_INCALL_PROJECTING, projecting)
        if (projecting) editor.putBoolean(KEY_EXT_SCREEN_RELEASED, false)
        editor.apply()
    }

    private fun warmStart(): Boolean = persistedProjecting()
    private var openState = false
    private var broadcastHalfSent = false
    private var silentOpenOnly = false
    private var silentHoldScheduled = false
    private var sourceEnterSent = false
    private var generation = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private val token = Any()

    private val navigateCallback = object : Binder(), IInterface {
        init {
            attachInterface(this, CALLBACK_TOKEN)
        }

        override fun asBinder(): IBinder = this

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            when (code) {
                1598968902 -> {
                    reply?.writeString(CALLBACK_TOKEN)
                    return true
                }
                1, 2 -> {
                    data.enforceInterface(CALLBACK_TOKEN)
                    val value = data.readInt()
                    callbackHeard = true
                    AppLog.i("InCallClusterBridge: INavigateCallback code=$code value=$value")
                    if (code == CALLBACK_CODE_STATE) {
                        val projecting = value == CALLBACK_PROJECTING
                        if (projecting && releasedByUs() && withinR0AnswerWindow()) {
                            AppLog.i("InCallClusterBridge: r0 answer ignored for open decision: value=3 ${SystemClock.uptimeMillis() - r0SentAtUptimeMs}ms after hand-back")
                        } else {
                            if (projecting && releasedByUs() && r0SentAtUptimeMs == 0L) {
                                setReleasedByUs(false)
                            }
                            persistProjecting(projecting)
                        }
                    }
                    val previous = lastCallbackValue.put(code, value)
                    if (previous == null || previous != value) {
                        diagnosticSink?.invoke("ClusterBridge callback: code=$code value=$value" + (previous?.let { " (was $it)" } ?: ""))
                        if (code == CALLBACK_CODE_STATE) {
                            val nowProjecting = incallReportsProjecting()
                            projectingListener?.invoke(nowProjecting)
                        }
                    }
                    reply?.writeNoException()
                    return true
                }
            }
            return super.onTransact(code, data, reply, flags)
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            AppLog.i("InCallClusterBridge: service connected: $name")
            binder = service
            bound = true
            if (observeCallbackRequested && !callbackRegistered) {
                val ok = registerCallback(service ?: return)
                logSeq(generation, "observe: registered INavigateCallback before activation -> $ok")
            }
            if (openState) {
                if (silentOpenOnly) runSilentOpen() else runFullOpenSequence()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            AppLog.w("InCallClusterBridge: service disconnected: $name")
            binder = null
            bound = false
            callbackRegistered = false
        }
    }

    fun observeCallback(context: Context) {
        contextRef = context.applicationContext
        if (observeCallbackRequested) return
        observeCallbackRequested = true
        logSeq(
            generation,
            "observe: persisted state projecting=${persistedProjecting()} released=${persistedReleased()} releasedByUs=${releasedByUs()} heard=$callbackHeard"
        )
        val existing = binder
        if (existing != null) {
            val ok = runCatching { registerCallback(existing) }.getOrDefault(false)
            logSeq(generation, "observe: registered INavigateCallback before activation -> $ok")
        } else {
            logSeq(generation, "observe: binding to watch INavigateCallback before activation")
            ensureBound(context)
        }
    }

    fun isAvailable(context: Context): Boolean {
        contextRef = context.applicationContext
        return try {
            val pm = context.packageManager
            pm.getApplicationInfo(PKG_INCALL, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun openProjection(context: Context) {
        contextRef = context.applicationContext
        val ctx = contextRef ?: return
        mainHandler.removeCallbacksAndMessages(token)
        generation++
        val alreadyProjecting = incallReportsProjecting() && !releasedByUs()
        openState = true
        broadcastHalfSent = false
        sourceEnterSent = false
        silentHoldScheduled = false
        silentOpenOnly = alreadyProjecting
        ensureBound(ctx)
        if (alreadyProjecting) {
            logSeq(generation, "open: incall already reports projecting; silent half only, no source switch")
            runSilentOpen()
            return
        }
        if (!lastCallbackValue.containsKey(CALLBACK_CODE_STATE) && warmStart() && !releasedByUs()) {
            silentOpenOnly = true
            logSeq(
                generation,
                "open: fresh process, persisted state projecting=${persistedProjecting()} released=${persistedReleased()}; silent half first, full sequence in ${SILENT_CONFIRM_MS}ms unless incall confirms"
            )
            runSilentOpen()
            armSilentOpenFallback()
            return
        }
        if (binder != null) {
            runFullOpenSequence()
        } else {
            logSeq(generation, "incall service not connected; waiting up to ${SERVICE_WAIT_FALLBACK_MS}ms")
            val gen = generation
            mainHandler.postAtTime({
                if (gen != generation) return@postAtTime
                if (binder != null) return@postAtTime
                logSeq(gen, "incall service still not connected; broadcast half only")
                runBroadcastHalf()
            }, token, SystemClock.uptimeMillis() + SERVICE_WAIT_FALLBACK_MS)
        }
    }

    fun reassertProjection(context: Context) {
        contextRef = context.applicationContext
        if (!openState) {
            logSeq(generation, "reassert skipped: projection not open")
            return
        }
        logSeq(generation, "reassert: F0 + C0 + Z (broadcast only, no source switch)")
        clearScreenOpen()
        setCarProperties()
        if (binder == null) return
        setMode(1)
        setMode(3)
    }

    fun releaseExternalScreen(context: Context) {
        contextRef = context.applicationContext
        logSeq(generation, "releaseExternalScreen: r0 ext-screen hand-back (no binder teardown)")
        openState = false
        generation++
        mainHandler.removeCallbacksAndMessages(token)
        lastCallbackValue[CALLBACK_CODE_STATE] = 0
        r0SentAtUptimeMs = SystemClock.uptimeMillis()
        prefs()?.edit()
            ?.putBoolean(KEY_INCALL_PROJECTING, false)
            ?.putBoolean(KEY_EXT_SCREEN_RELEASED, true)
            ?.putBoolean(KEY_EXT_SCREEN_RELEASED_BY_US, true)
            ?.apply()
        broadcastOperateAll(2, 2)
        broadcastStatusAll(2, 2)
        broadcastOperateTo(PKG_INCALL, 0, 1)
        broadcastStatusTo(PKG_INCALL, 0, 1)
    }

    private fun runFullOpenSequence() {
        r0SentAtUptimeMs = 0L
        setReleasedByUs(false)
        runBroadcastHalf()
        runOpenLocked()
    }

    private fun runSilentOpen() {
        broadcastHalfSent = true
        clearScreenOpen()
        setCarProperties()
        setMode(1)
        setMode(3)
        if (silentHoldScheduled) return
        silentHoldScheduled = true
        for (at in HOLD_RETRY_DELAYS) {
            mainHandler.postAtTime({
                if (!alive()) return@postAtTime
                clearScreenOpen()
                setMode(3)
                setCarProperties()
            }, token, SystemClock.uptimeMillis() + at)
        }
    }

    private fun armSilentOpenFallback() {
        val gen = generation
        mainHandler.postAtTime({
            if (gen != generation || !openState) return@postAtTime
            if (incallReportsProjecting()) {
                logSeq(gen, "silent open confirmed by incall (projecting); no full sequence needed")
                return@postAtTime
            }
            logSeq(gen, "silent open not confirmed within ${SILENT_CONFIRM_MS}ms; running the full open sequence")
            silentOpenOnly = false
            broadcastHalfSent = false
            sourceEnterSent = false
            runFullOpenSequence()
        }, token, SystemClock.uptimeMillis() + SILENT_CONFIRM_MS)
    }

    private fun runBroadcastHalf() {
        if (broadcastHalfSent) return
        broadcastHalfSent = true
        runOpenBroadcastSide()
    }

    private fun ensureBound(ctx: Context) {
        if (bound && binder != null) return
        if (bound && binder == null) {
            try {
                ctx.unbindService(connection)
            } catch (_: IllegalArgumentException) {
            }
            bound = false
        }
        if (bound) return
        val intent = Intent(SERVICE_ACTION).setComponent(
            ComponentName(PKG_INCALL, SERVICE_CLASS),
        )
        try {
            bound = ctx.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            AppLog.i("InCallClusterBridge: bindService -> $bound")
        } catch (e: RuntimeException) {
            bound = false
            AppLog.w("InCallClusterBridge: bindService failed: ${e.message}")
        }
    }

    private fun runOpenBroadcastSide() {
        val gen = generation
        logSeq(gen, "E0: F0 + A0 state ladder + p0 + Z + J0")
        clearScreenOpen()
        var t = 0L
        for (s in STATE_LADDER) {
            val at = t
            val state = s
            mainHandler.postAtTime({
                if (gen != generation) return@postAtTime
                broadcastSource(state)
                if (state == -1) broadcastStatusOpen()
            }, token, SystemClock.uptimeMillis() + at)
            t += 90L
        }
        sendSourceEnter()
        for ((i, d) in SOURCE_SWITCH_DELAYS.withIndex()) {
            mainHandler.postAtTime({
                if (gen != generation) return@postAtTime
                broadcastSource(if (i == 0) 12 else 40)
            }, token, SystemClock.uptimeMillis() + d.toLong())
        }
        setCarProperties()
    }

    private fun runOpenLocked() {
        val b = binder ?: return
        val gen = generation
        logSeq(gen, "G0: register + F0 + 9 + n0(F0) + C0(1) + C0(3) + Z + tx2 + hold")
        registerCallback(b)
        clearScreenOpen()
        sendSourceEnter()
        repeatAt(OPEN_RETRY_DELAYS) {
            if (gen != generation) return@repeatAt
            if (!alive()) return@repeatAt
            clearScreenOpen()
        }
        setMode(1)
        setMode(3)
        setCarProperties()
        mainHandler.postAtTime({
            if (gen != generation) return@postAtTime
            if (!alive()) return@postAtTime
            val ok = txWrite(b, 2, intArrayOf(1))
            logSeq(gen, "tx2[1] -> $ok")
        }, token, SystemClock.uptimeMillis() + 90L)
        repeatAt(HOLD_RETRY_DELAYS) {
            if (gen != generation) return@repeatAt
            if (!alive()) return@repeatAt
            clearScreenOpen()
            setMode(3)
            setCarProperties()
        }
    }

    private fun alive(): Boolean {
        val b = binder
        if (b == null || !b.isBinderAlive) {
            binder = null
            ensureBound(contextRef ?: return false)
            return binder != null
        }
        return true
    }

    private fun logSeq(gen: Int, msg: String) {
        AppLog.i("InCallClusterBridge: [seq $gen] $msg")
        runCatching { diagnosticSink?.invoke("ClusterBridge: $msg") }
    }

    @Volatile
    var diagnosticSink: ((String) -> Unit)? = null

    @Volatile
    var projectingListener: ((Boolean) -> Unit)? = null

    private fun registerCallback(b: IBinder): Boolean {
        val ok = txBinder(b, 29, navigateCallback.asBinder())
        callbackRegistered = ok
        logSeq(generation, "registerCallback tx29(INavigateCallback) -> $ok")
        return ok
    }

    fun unregisterCallback(b: IBinder) {
        if (!callbackRegistered) return
        val ok = txBinder(b, 30, navigateCallback.asBinder())
        callbackRegistered = false
        logSeq(generation, "unregisterCallback tx30 -> $ok")
    }

    private fun setMode(mode: Int) {
        val b = binder ?: return
        txWrite(b, 21, intArrayOf(1, 0))
        txWrite(b, 8, intArrayOf(mode, 0))
        txWrite(b, 1, intArrayOf(0))
        logSeq(generation, "setMode($mode) done")
    }

    private fun clearScreenOpen() {
        broadcastOperateAll(0, 2)
        broadcastStatusAll(0, 2)
    }

    private fun sendSourceEnter() {
        if (sourceEnterSent) return
        sourceEnterSent = true
        broadcastSource(9)
    }

    private fun broadcastSource(state: Int) {
        val ctx = contextRef ?: return
        val intent = Intent(ACTION_AUTONAVI).apply {
            setPackage(PKG_INCALL)
            putExtra(EXTRA_KEY_TYPE, 10019)
            putExtra(EXTRA_STATE, state)
            putExtra(EXTRA_CROSS_MAP, -1)
            addFlags(0x1000000)
        }
        runCatching { ctx.sendBroadcast(intent) }
            .onSuccess { logSeq(generation, "broadcast 10019 state=$state") }
            .onFailure { AppLog.w("InCallClusterBridge: broadcast 10019 state=$state failed: ${it.message}") }
    }

    private fun broadcastOperateAll(op: Int, mode: Int) {
        for (pkg in EXTERNAL_SCREEN_PKGS) broadcastOperateTo(pkg, op, mode)
    }

    private fun broadcastOperateTo(pkg: String, op: Int, mode: Int) {
        val ctx = contextRef ?: return
        val intent = Intent(ACTION_AUTONAVI).apply {
            setPackage(pkg)
            putExtra(EXTRA_KEY_TYPE, 10116)
            putExtra(EXTRA_OPERATE, op)
            putExtra(EXTRA_WINDOW, 1001)
            putExtra(EXTRA_MODE, mode)
            addFlags(0x1000000)
        }
        runCatching { ctx.sendBroadcast(intent) }
            .onSuccess { logSeq(generation, "broadcast 10116 op=$op mode=$mode -> $pkg") }
            .onFailure { AppLog.w("InCallClusterBridge: broadcast 10116 -> $pkg failed: ${it.message}") }
    }

    private fun broadcastStatusAll(status: Int, mode: Int) {
        for (pkg in EXTERNAL_SCREEN_PKGS) broadcastStatusTo(pkg, status, mode)
    }

    private fun broadcastStatusOpen() {
        broadcastStatusAll(0, 2)
    }

    private fun broadcastStatusTo(pkg: String, status: Int, mode: Int) {
        val ctx = contextRef ?: return
        val intent = Intent(ACTION_AUTONAVI).apply {
            setPackage(pkg)
            putExtra(EXTRA_KEY_TYPE, 10117)
            putExtra(EXTRA_STATUS, status)
            putExtra(EXTRA_WINDOW, 1001)
            putExtra(EXTRA_MODE, mode)
            addFlags(0x1000000)
        }
        runCatching { ctx.sendBroadcast(intent) }
            .onSuccess { logSeq(generation, "broadcast 10117 status=$status mode=$mode -> $pkg") }
            .onFailure { AppLog.w("InCallClusterBridge: broadcast 10117 -> $pkg failed: ${it.message}") }
    }

    private fun setCarProperties() {
        val ctx = contextRef ?: return
        try {
            val carClass = Class.forName("android.car.Car")
            val createCar = carClass.getMethod("createCar", Context::class.java)
            val car = createCar.invoke(null, ctx) ?: return
            val manager = carClass.getMethod("getCarManager", String::class.java)
                .invoke(car, "instrument_panel") ?: return
            val setter = manager.javaClass.getMethod(
                "setIntProperty",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            setter.invoke(manager, CAR_PROP_SOURCE, 0, 3)
            setter.invoke(manager, CAR_PROP_MODE, 0, 1)
            logSeq(generation, "CarProperty setIntProperty(557855845,3)/(557855847,1) ok")
        } catch (e: Exception) {
            AppLog.w("InCallClusterBridge: CarProperty set failed (non-system app likely): ${e.message}")
        }
    }

    private fun txWrite(b: IBinder, code: Int, ints: IntArray): Boolean {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(BINDER_TOKEN)
            for (v in ints) data.writeInt(v)
            val ok = b.transact(code, data, reply, 0)
            runCatching { reply.readException() }
            return ok
        } catch (e: Exception) {
            AppLog.w("InCallClusterBridge: tx$code ${ints.contentToString()} failed: ${e.message}")
            return false
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun txBinder(b: IBinder, code: Int, callback: IBinder): Boolean {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(BINDER_TOKEN)
            data.writeStrongBinder(callback)
            val ok = b.transact(code, data, reply, 0)
            runCatching { reply.readException() }
            return ok
        } catch (e: Exception) {
            AppLog.w("InCallClusterBridge: tx$code binder failed: ${e.message}")
            return false
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun repeatAt(delays: LongArray, action: () -> Unit) {
        for (d in delays) {
            mainHandler.postAtTime({ action() }, token, SystemClock.uptimeMillis() + d)
        }
    }
}
