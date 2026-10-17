package app.yatori.android

import android.app.Application
import android.util.Log
import java.util.TimeZone

/**
 * Process-wide Android bootstrap.
 *
 * The Go core and the Android UI run in the same process.  Some devices expose
 * a UTC default zone even when the user-facing system clock is set to China
 * time, which made timestamps in the task log appear eight hours behind.  Set
 * the Java process defaults before the native core is initialised so every
 * entry point (activity, service and JNI callbacks) starts with one zone.
 */
class YatoriApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        YatoriTime.configure()
        Log.i(TAG, "Process timezone=${TimeZone.getDefault().id}")
    }

    companion object {
        private const val TAG = "YatoriAndroid"
    }
}

internal object YatoriTime {
    const val ZONE_ID = "Asia/Shanghai"
    private val ZONE: TimeZone = TimeZone.getTimeZone(ZONE_ID)

    @Synchronized
    fun configure() {
        // Keep this defensive: a malformed vendor runtime must not prevent the
        // app from opening.  Asia/Shanghai is a fixed, always-present Android
        // tzdata zone and does not observe DST.
        try {
            TimeZone.setDefault(ZONE)
            System.setProperty("user.timezone", ZONE_ID)
        } catch (_: Throwable) {
            // Best effort only.  Callers still get a usable application even
            // if a vendor security policy rejects changing a process default.
        }
    }
}
