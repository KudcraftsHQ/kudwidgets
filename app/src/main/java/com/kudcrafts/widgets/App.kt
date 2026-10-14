package com.kudcrafts.widgets

import android.app.Application
import com.kudcrafts.widgets.work.Refresher

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Idempotent (KEEP): every process start makes sure the 15-minute refresh exists.
        Refresher.schedule(this)
    }
}
