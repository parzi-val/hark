package com.hark

import android.app.Application
import com.hark.di.AppContainer

class HarkApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
