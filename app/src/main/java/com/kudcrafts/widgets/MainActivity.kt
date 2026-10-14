package com.kudcrafts.widgets

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kudcrafts.widgets.ui.Palette

/** Phase 1: a plain about screen. The live gallery replaces it in phase 4. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val version = BuildConfigInfo.versionName(this)
        setContent {
            Column(
                modifier = Modifier.fillMaxSize().background(Palette.surface).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BasicText(
                    "Kud Widgets",
                    style = TextStyle(color = Palette.cream, fontSize = 28.sp, fontWeight = FontWeight.Bold),
                )
                BasicText("v$version", style = TextStyle(color = Palette.creamDim, fontSize = 14.sp))
                BasicText(
                    "Long-press the home screen › Widgets › Kud Widgets to add one.",
                    style = TextStyle(color = Palette.cream, fontSize = 16.sp),
                )
            }
        }
    }
}
