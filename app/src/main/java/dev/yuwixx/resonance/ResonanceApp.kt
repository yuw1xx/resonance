package dev.yuwixx.resonance

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dev.yuwixx.resonance.data.worker.AutoScanManager
import javax.inject.Inject

@HiltAndroidApp
class ResonanceApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var autoScanManager: AutoScanManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        autoScanManager.initialize()
    }
}