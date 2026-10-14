package com.kudcrafts.widgets.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The app's few pieces, in the widgets' night palette. No Material: nothing here should look stock. */
object AppUi {
    val card = Palette.surface

    @Composable
    fun Section(title: String) = BasicText(
        title.uppercase(),
        style = TextStyle(color = Palette.creamDim, fontSize = 12.sp, letterSpacing = 1.sp),
        modifier = Modifier.padding(top = 10.dp),
    )

    @Composable
    fun Title(text: String) = BasicText(text, style = TextStyle(color = Palette.cream, fontSize = 17.sp, fontWeight = FontWeight.Bold))

    @Composable
    fun Body(text: String, color: Color = Palette.cream) = BasicText(text, style = TextStyle(color = color, fontSize = 14.sp))

    @Composable
    fun Dim(text: String) = BasicText(text, style = TextStyle(color = Palette.creamDim, fontSize = 13.sp))

    @Composable
    fun Button(label: String, onClick: () -> Unit) = BasicText(
        label,
        style = TextStyle(color = Palette.cream, fontSize = 14.sp),
        modifier = Modifier
            .border(1.dp, Palette.cream.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )

    /** A choice among a few: the chosen one is a quiet cream tint, never a highlight pill. */
    @Composable
    fun Choice(label: String, selected: Boolean, onClick: () -> Unit) = BasicText(
        label,
        style = TextStyle(color = if (selected) Palette.cream else Palette.creamDim, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
        modifier = Modifier
            .background(if (selected) Palette.cream.copy(alpha = 0.12f) else Color.Transparent, RoundedCornerShape(10.dp))
            .border(1.dp, Palette.cream.copy(alpha = if (selected) 0.30f else 0.14f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}
