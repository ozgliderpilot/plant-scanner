package com.nursery.scanner.ci

import com.nursery.scanner.di.AppContainer

/**
 * Optional CI activate hook. Registered from qaDebug [CiNurseryApplication]; null in prod/qaRelease.
 */
object CiModeHooks {
    @Volatile
    var activate: (suspend (AppContainer) -> Unit)? = null
}
