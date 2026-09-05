package net.breadthcharge.exigentheron

import android.app.Application

class App : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        SafeLog.lifecycle("App.onCreate")
    }
}
