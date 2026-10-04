package com.rovia.music

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class RoviaApplication : Application() {

    lateinit var appContainer: AppContainer
        private set

    val applicationScope: CoroutineScope by lazy {
        CoroutineScope(
            SupervisorJob() + Dispatchers.Default,
        )
    }

    override fun onCreate() {
        super.onCreate()

        appContainer = AppContainer(
            context = this,
            applicationScope = applicationScope,
        )
    }
}