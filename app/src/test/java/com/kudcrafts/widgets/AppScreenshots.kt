package com.kudcrafts.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.kudcrafts.widgets.store.CacheStore
import com.kudcrafts.widgets.core.Cached
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import android.os.Looper

/** The app's own screen (the gallery), signed out: what a fresh install opens to. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h1400dp-xxhdpi", application = android.app.Application::class)
class AppScreenshots {
    @Test fun gallerySignedOut() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(ctx)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        repeat(5) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(200) }
        val root: View = activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        val out = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
        File(out, "app-gallery-signed-out.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
