package com.kudcrafts.widgets.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.kudcrafts.widgets.MainActivity
import com.kudcrafts.widgets.OpenActivity
import com.kudcrafts.widgets.R
import com.kudcrafts.widgets.core.AuthStatus
import com.kudcrafts.widgets.core.Display
import com.kudcrafts.widgets.core.Format
import com.kudcrafts.widgets.core.Jakarta
import com.kudcrafts.widgets.store.Source
import com.kudcrafts.widgets.ui.Palette

/** The widget surface: opaque night, 22dp corners (a drawable below API 31, where cornerRadius is a no-op). */
fun GlanceModifier.surface(): GlanceModifier =
    background(ImageProvider(R.drawable.widget_surface)).cornerRadius(22.dp)

val WidgetPadH: Dp = 16.dp
val WidgetPadV: Dp = 12.dp

fun cream(dim: Boolean = false) = ColorProvider(if (dim) Palette.creamDim else Palette.cream)

fun text(size: TextUnit, color: Color = Palette.cream, bold: Boolean = false) =
    TextStyle(color = ColorProvider(color), fontSize = size, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)

/** Tap: refresh in the background and open `url` in the browser. */
fun openAction(context: Context, url: String): Action =
    actionStartActivity(OpenActivity.intent(context, url))

/** Tap on a signed-out widget: the app's sign-in for that source. */
fun signInAction(context: Context, source: Source): Action =
    actionStartActivity(
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_SIGN_IN, source.key)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
    )

/** What the header's right-hand slot says: the data time when fresh, its age otherwise. */
fun headerTime(display: Display<*>): Pair<String?, Color> = when (display) {
    is Display.Data<*> -> when {
        display.error != null -> Format.age(display.ageMinutes) to Palette.downText
        display.stale -> Format.age(display.ageMinutes) to Palette.creamDim
        else -> Jakarta.clock(display.fetchedAt) to Palette.creamDim
    }
    else -> null to Palette.creamDim
}

/** Loading, sign-in and first-fetch failure share one quiet layout under the header. */
@Composable
fun NonDataBody(context: Context, display: Display<*>, source: Source, compact: Boolean) {
    val (title, line) = when (display) {
        Display.Loading -> "Loading…" to null
        is Display.SignIn -> "Sign in" to when (display.reason) {
            AuthStatus.Refused -> "${source.label} refused the key · tap to sign in again"
            AuthStatus.Expired -> "The key expired · tap to sign in again"
            else -> "Tap to connect ${source.label}"
        }
        is Display.Failed -> "Can't reach ${source.label}" to display.error
        is Display.Data<*> -> return
    }
    Column(verticalAlignment = Alignment.Top) {
        Spacer(GlanceModifier.height(if (compact) 2.dp else 6.dp))
        Text(title, style = text(if (compact) 18.sp else 24.sp, if (display == Display.Loading) Palette.creamDim else Palette.cream), maxLines = 1)
        if (line != null && !compact) {
            Spacer(GlanceModifier.height(2.dp))
            Text(line, style = text(12.sp, Palette.creamDim), maxLines = 2)
        }
    }
}

/** The whole-widget tap for a non-data state. */
fun nonDataAction(context: Context, display: Display<*>, source: Source, url: String): Action =
    if (display is Display.SignIn) signInAction(context, source) else openAction(context, url)

/** A plain label-over-value column: "Orders / 64". */
@Composable
fun Stat(label: String, value: String, dim: Boolean, valueSize: TextUnit = 18.sp) {
    Column(horizontalAlignment = Alignment.End) {
        Text(label, style = text(11.sp, Palette.creamDim), maxLines = 1)
        Text(value, style = text(valueSize, if (dim) Palette.creamDim else Palette.cream), maxLines = 1)
    }
}
