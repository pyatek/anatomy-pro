package com.ptk.anatomypro.core.designsystem

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * WCAG relative luminance of an ARGB colour, 0f (black) to 1f (white).
 *
 * Used to prove highlight states stay distinguishable without hue (spec §12).
 */
fun relativeLuminance(argb: Int): Float {
    fun channel(shift: Int): Float {
        val srgb = ((argb shr shift) and 0xFF) / 255f
        return if (srgb <= 0.03928f) srgb / 12.92f else ((srgb + 0.055f) / 1.055f).pow(2.4f)
    }
    return 0.2126f * channel(16) + 0.7152f * channel(8) + 0.0722f * channel(0)
}

/** WCAG contrast ratio between two ARGB colours: 1f (identical) to 21f (black on white). */
fun contrastRatio(a: Int, b: Int): Float {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
}

// ── Dark palette: the atlas and quiz screens ──
// A deep, slightly cool neutral so the model sits on something with depth, not a void.
val Ground = Color(0xFF0D1012)
val Surface = Color(0xFF15191C)
val Hairline = Color(0xFF262C31)
val TextPrimary = Color(0xFFE9ECEE)
val TextSecondary = Color(0xFF9AA3AA)
val TextTertiary = Color(0xFF8A9299)
val CanvasAnnotation = Color(0xFF828B92)

/**
 * The bottom bar sits between [Ground] and [Surface], on a hairline darker than [Hairline].
 *
 * Both are the prototype's values. The bar reads as part of the chrome rather than as a
 * card floating above the content, which is why it is not simply [Surface].
 */
val NavSurface = Color(0xFF101417)
val NavHairline = Color(0xFF1F252A)

/** The single accent. Carries selection and primary action — nothing else. */
val Accent = Color(0xFFE8604F)
val HighlightFill = Color(0xFFF07C69)
val HighlightOutline = Color(0xFFFFD3CB)

/** Correct and incorrect are separate hues *and* separate shapes; see [HighlightTokens]. */
val CorrectGreen = Color(0xFF57B37C)
val IncorrectAmber = Color(0xFFD89B3C)

// ── Controls and cards, read off the prototype screens (spec §19) ──
/** Ink on an [Accent] fill: the primary button's label, a ticked checkbox's tick. */
val OnAccent = Color(0xFF150C0A)
/** A chosen card's fill, one step up from [Surface]. */
val SurfaceRaised = Color(0xFF1C2126)
/** An unticked checkbox, an idle control's outline. */
val ControlOutline = Color(0xFF4B545B)
/** The rule above a screen's footer. */
val FooterHairline = Color(0xFF1A1F23)
/** The outline of a "free" badge; its text is [CorrectGreen]. */
val FreeOutline = Color(0xFF2E5541)
/** A quieter accent outline, for a chosen plan or a marked row. */
val AccentMuted = Color(0xFF7A2E25)
/** The square behind a monogram or a glyph on a row. */
val MonogramFill = Color(0xFF1E2429)
/** The warm top of the daily quiz's card, fading into [Surface]. */
val AccentWash = Color(0xFF22100D)
/** Text that leads without being a heading: values, the user's own row. */
val TextStrong = Color(0xFFC9D0D6)

// ── Light palette: the two reading-heavy screens ──
val LightGround = Color(0xFFF3F4F5)
val LightTextPrimary = Color(0xFF14181B)
val LightTextTertiary = Color(0xFF5D666D)
val AccentOnLight = Color(0xFFB23A2E)
val AccentOnLightSurface = Color(0xFFFFF3F1)

/**
 * Converts to the plain ARGB int the renderer boundary uses, keeping renderer-api free of
 * Compose types so a non-Compose renderer can still consume these tokens.
 */
internal fun Color.toArgbInt(): Int {
    fun component(value: Float): Int = (value * 255f + 0.5f).toInt() and 0xFF
    return (component(alpha) shl 24) or
        (component(red) shl 16) or
        (component(green) shl 8) or
        component(blue)
}
