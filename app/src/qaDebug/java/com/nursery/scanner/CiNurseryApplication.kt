package com.nursery.scanner

import com.nursery.scanner.ci.CiBootstrap
import com.nursery.scanner.ci.CiMode

/**
 * qaDebug Application: registers CI screenshot bootstrap before [MainActivity] runs.
 */
class CiNurseryApplication : NurseryApplication() {
    override fun onCreate() {
        CiMode.activate = CiBootstrap::activate
        super.onCreate()
    }
}
