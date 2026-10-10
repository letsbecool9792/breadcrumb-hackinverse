package com.lbc.breadcrumb.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.lbc.breadcrumb.R

// Three faces, bundled (res/font, licences in assets/licenses -- all OFL).
// One face per job, and the split is the point:
//
//   Serif -- the app's voice, and a memory once it is opened: the wordmark, a
//            detail's title, a note read in full.
//   Sans  -- everything on the page: tiles, result rows, captions, summaries.
//   Mono  -- chrome: counts, dates, source apps, match fragments.
//
// A memory's title is sans wherever it sits among others and serif once it
// is opened -- for every kind of memory, never by kind.

/** Young Serif: a sturdy old-style serif with a large x-height, in one regular weight. */
val Serif = FontFamily(Font(R.font.young_serif, FontWeight.Normal))

/**
 * Instrument Sans: one variable file serving every weight used here. Setting
 * a variable font's weight is still experimental in Compose; the fallback if
 * it ever breaks is the same file at its default weight, not a missing font.
 */
@OptIn(ExperimentalTextApi::class)
val Sans = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { weight ->
        Font(
            R.font.instrument_sans,
            weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
        )
    }
)

/** IBM Plex Mono, for chrome. */
val Mono = FontFamily(
    Font(R.font.ibm_plex_mono, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
)

private val material = Typography()

private fun TextStyle.inSerif() = copy(fontFamily = Serif, fontWeight = FontWeight.Normal)
private fun TextStyle.inSans() = copy(fontFamily = Sans)

/**
 * Material's scale in the brand faces, so any Text that does not name a style
 * is set in Instrument Sans, and Material's display and headline sizes in the
 * serif. A Text given its own TextStyle takes nothing from this: name the face
 * (Serif, Sans, Mono) in every style you build, or it falls back to the
 * platform font.
 */
val Typography = Typography(
    displayLarge = material.displayLarge.inSerif(),
    displayMedium = material.displayMedium.inSerif(),
    displaySmall = material.displaySmall.inSerif(),
    headlineLarge = material.headlineLarge.inSerif(),
    headlineMedium = material.headlineMedium.inSerif(),
    headlineSmall = material.headlineSmall.inSerif(),
    titleLarge = material.titleLarge.inSans(),
    titleMedium = material.titleMedium.inSans(),
    titleSmall = material.titleSmall.inSans(),
    bodyLarge = material.bodyLarge.inSans(),
    bodyMedium = material.bodyMedium.inSans(),
    bodySmall = material.bodySmall.inSans(),
    labelLarge = material.labelLarge.inSans(),
    labelMedium = material.labelMedium.inSans(),
    labelSmall = material.labelSmall.inSans(),
)
