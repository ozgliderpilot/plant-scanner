package com.nursery.scanner.ci

import android.content.Intent
import com.nursery.scanner.di.AppContainer

/**
 * Process-wide CI screenshot mode (#72). Defaults keep normal installs inert.
 * qaDebug registers [activate]; [onColdStart] runs it when the launch intent carries [EXTRA_CI_MODE].
 */
object CiMode {
    const val EXTRA_CI_MODE = "com.nursery.scanner.CI_MODE"

    @Volatile
    var active: Boolean = false
        internal set

    /** Set from qaDebug [com.nursery.scanner.CiNurseryApplication]; null in prod/qaRelease. */
    @Volatile
    var activate: (suspend (AppContainer) -> Unit)? = null

    private fun clear() {
        active = false
    }

    /**
     * Called from [com.nursery.scanner.MainActivity] on cold start.
     * Runs [activate] when the launch intent carries [EXTRA_CI_MODE]; otherwise clears flags.
     */
    suspend fun onColdStart(container: AppContainer, intent: Intent?) {
        if (intent == null || !intent.hasCiModeExtra()) {
            clear()
            return
        }
        val hook = activate
        if (hook == null) {
            clear()
            return
        }
        hook(container)
    }

    /** True when the launch intent requested CI mode (boolean or string "true" from Maestro/adb). */
    private fun Intent.hasCiModeExtra(): Boolean {
        if (getBooleanExtra(EXTRA_CI_MODE, false)) return true
        val raw = extras?.getString(EXTRA_CI_MODE) ?: return false
        return raw.equals("true", ignoreCase = true)
    }
}
