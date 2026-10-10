package com.lbc.breadcrumb.ui.theme

import androidx.compose.ui.graphics.Color

// Keyed to the launcher icon: amber crumbs on deep warm ink.
// Ink matches @color/brand_ink so the splash hands off without a colour jump.

val Ink = Color(0xFF171310)
val InkElevated = Color(0xFF221B16)
val InkBorder = Color(0xFF2E2620)
val InkOutline = Color(0xFF7A6E62)
val Bone = Color(0xFFEDE3D8)
val BoneDim = Color(0xFFBFB2A4)

val AmberBright = Color(0xFFFFC46B)
val AmberDeep = Color(0xFF8A5A00)
val AmberContainerDark = Color(0xFF5A4420)
val AmberContainerLight = Color(0xFFFFDCA8)
val AmberOnContainer = Color(0xFF2C1A00)

val Parchment = Color(0xFFFFF8F3)
val ParchmentVariant = Color(0xFFF0E0CF)
val ParchmentBorder = Color(0xFFE2D3C3)
val Umber = Color(0xFF1F1B16)
val UmberDim = Color(0xFF4F4539)

// The crumb trail, faintest to brightest, as the logo draws it. Fixed in both
// themes: this is the mark, not UI chrome.
val CrumbFaint = Color(0xFF5A4420)
val CrumbDim = Color(0xFF7A5A28)
val CrumbMid = Color(0xFFC8912F)
val CrumbBright = Color(0xFFFFC46B)

/** Dims the host app behind the capture sheet. */
val CaptureScrim = Color(0xFF080605)

// The search screens. The app is a frame around other people's screenshots,
// so everything here sits a step off the ink.
/** Behind a thumbnail or a page: a shade under the lifted surface. */
val InkMedia = Color(0xFF1C1713)
/** The search field's edge at rest; it turns [AmberContainerDark] while typing. */
val InkFieldBorder = Color(0xFF322A23)
/** A sheet's grabber and the empty half of a placeholder line. */
val InkGrabber = Color(0xFF3A322A)
/** Section labels that should barely be there ("text found"). */
val InkLabel = Color(0xFF5A4E44)
/** Behind the matched word in a result's fragment. */
val HitBackground = Color(0xFF3A2C14)
