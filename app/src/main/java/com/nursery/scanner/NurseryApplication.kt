package com.nursery.scanner

import android.app.Application
import com.nursery.scanner.di.AppContainer

class NurseryApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Start the silent 1-minute auto-export ticker (spec #10).
        container.autoExportTicker.start()
    }
}
