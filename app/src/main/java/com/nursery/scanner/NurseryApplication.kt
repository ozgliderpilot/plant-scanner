package com.nursery.scanner

import android.app.Application
import com.nursery.scanner.di.AppContainer

class NurseryApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Auto-export ticker starts from MainActivity after CI mode may stop it (#72).
    }
}
