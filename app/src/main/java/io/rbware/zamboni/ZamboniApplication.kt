package io.rbware.zamboni

import android.app.Application
import io.rbware.zamboni.di.AppContainer

class ZamboniApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
