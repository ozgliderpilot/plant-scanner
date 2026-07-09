package com.nursery.scanner.ci

import android.content.Intent
import com.nursery.scanner.di.AppContainer

/**
 * Process-wide CI mode flags and cold-start hook (#72).
 *
 * Defaults keep normal qaDebug / prod / qaRelease installs inert. The real activator is registered
 * only from the qaDebug source set; [onColdStart] is a no-op until then.
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

    @Volatile
    internal var activator: (suspend (AppContainer) -> Unit)? = null

    /**
     * Called from [com.nursery.scanner.MainActivity] on cold start. Activates when the launch
     * intent carries [EXTRA_CI_MODE] and a qaDebug activator is registered; otherwise clears
     * process-wide CI flags so a normal relaunch after CI does not keep placeholder/export gating.
     */
    suspend fun onColdStart(container: AppContainer, intent: Intent?) {
        if (intent == null || !intent.hasCiModeExtra()) {
            active = false
            useCameraPlaceholder = false
            skipCameraPermission = false
            return
        }
        activator?.invoke(container)
    }

    /** True when the launch intent requested CI mode (boolean or string "true" from Maestro/adb). */
    internal fun Intent.hasCiModeExtra(): Boolean {
        if (getBooleanExtra(EXTRA_CI_MODE, false)) return true
        val raw = extras?.getString(EXTRA_CI_MODE) ?: return false
        return raw.equals("true", ignoreCase = true)
    }
}
