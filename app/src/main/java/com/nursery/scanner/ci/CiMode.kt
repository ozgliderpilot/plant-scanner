package com.nursery.scanner.ci

import android.content.Intent
import com.nursery.scanner.di.AppContainer

/**
 * Process-wide CI screenshot flags (#72). Defaults keep normal installs inert.
 * [onColdStart] runs the qaDebug hook when the launch intent carries [EXTRA_CI_MODE].
 */
object CiMode {
    const val EXTRA_CI_MODE = "com.nursery.scanner.CI_MODE"

    @Volatile
    var active: Boolean = false
        internal set

    @Volatile
    var useCameraPlaceholder: Boolean = false
        internal set

    @Volatile
    var skipCameraPermission: Boolean = false
        internal set

    fun clear() {
        active = false
        useCameraPlaceholder = false
        skipCameraPermission = false
    }

    /**
     * Called from [com.nursery.scanner.MainActivity] on cold start.
     * Activates when the launch intent carries [EXTRA_CI_MODE] and a qaDebug hook is registered;
     * otherwise clears process-wide flags.
     */
    suspend fun onColdStart(container: AppContainer, intent: Intent?) {
        if (intent == null || !intent.hasCiModeExtra()) {
            clear()
            return
        }
        val hook = CiModeHooks.activate
        if (hook == null) {
            clear()
            return
        }
        hook(container)
    }

    /** True when the launch intent requested CI mode (boolean or string "true" from Maestro/adb). */
    internal fun Intent.hasCiModeExtra(): Boolean {
        if (getBooleanExtra(EXTRA_CI_MODE, false)) return true
        val raw = extras?.getString(EXTRA_CI_MODE) ?: return false
        return raw.equals("true", ignoreCase = true)
    }
}
