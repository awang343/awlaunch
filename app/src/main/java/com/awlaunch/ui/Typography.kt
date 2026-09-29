@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.awlaunch.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.awlaunch.R

private fun weightVariation(w: Int) = FontVariation.Settings(FontVariation.weight(w))

val CormorantGaramond: FontFamily = FontFamily(
    Font(R.font.cormorant_garamond, FontWeight.Light, FontStyle.Normal, variationSettings = weightVariation(300)),
    Font(R.font.cormorant_garamond, FontWeight.Normal, FontStyle.Normal, variationSettings = weightVariation(400)),
    Font(R.font.cormorant_garamond, FontWeight.Medium, FontStyle.Normal, variationSettings = weightVariation(500)),
    Font(R.font.cormorant_garamond, FontWeight.SemiBold, FontStyle.Normal, variationSettings = weightVariation(600)),
    Font(R.font.cormorant_garamond, FontWeight.Bold, FontStyle.Normal, variationSettings = weightVariation(700)),
    Font(R.font.cormorant_garamond_italic, FontWeight.Light, FontStyle.Italic, variationSettings = weightVariation(300)),
    Font(R.font.cormorant_garamond_italic, FontWeight.Normal, FontStyle.Italic, variationSettings = weightVariation(400)),
    Font(R.font.cormorant_garamond_italic, FontWeight.Medium, FontStyle.Italic, variationSettings = weightVariation(500)),
)

/** Warm off-white that disappears more gracefully over ornamental wallpapers than pure #FFF. */
val Cream: Color = Color(0xFFEDE3CC)
val CreamDim: Color = Color(0xFFEDE3CC).copy(alpha = 0.55f)
val CreamFaint: Color = Color(0xFFEDE3CC).copy(alpha = 0.18f)
