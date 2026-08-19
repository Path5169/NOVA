package com.nova.app

import android.app.Application

/**
 * NOVA has no backend, no analytics SDK, and no crash-reporting network calls.
 * This class exists purely as an extension point for local-only initialization
 * (e.g. warming a sensor manager reference) — never for telemetry.
 */
class NovaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
