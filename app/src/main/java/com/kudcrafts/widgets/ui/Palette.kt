package com.kudcrafts.widgets.ui

import androidx.compose.ui.graphics.Color

/** The one fixed night theme (docs/PLAN.md › Design). It ignores system light/dark on purpose. */
object Palette {
    val surface = Color(0xFF121013)
    val cream = Color(0xFFFEF8E8)
    /** Labels and timestamps: cream at ~58%. */
    val creamDim = cream.copy(alpha = 0.58f)
    val up = Color(0xFF79D3A0)
    val downText = Color(0xFFF0A58C)
    val downFill = Color(0xFFC8674B)
    val warnSurface = Color(0xFF1D1413)
}
