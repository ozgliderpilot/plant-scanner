package com.nursery.scanner

import com.nursery.scanner.ci.CiBootstrap
import com.nursery.scanner.ci.CiModeHooks

/**
 * qaDebug Application: registers CI screenshot bootstrap before [MainActivity] runs.
 */
class CiNurseryApplication : NurseryApplication() {
    override fun onCreate() {
        CiModeHooks.activate = { container -> CiBootstrap.activate(container) }
        super.onCreate()
    }
}
