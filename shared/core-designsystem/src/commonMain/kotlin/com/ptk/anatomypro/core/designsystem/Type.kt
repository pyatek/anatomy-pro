package com.ptk.anatomypro.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import anatomypro.shared.core_designsystem.generated.resources.Res
import anatomypro.shared.core_designsystem.generated.resources.ibm_plex_mono_medium
import anatomypro.shared.core_designsystem.generated.resources.ibm_plex_mono_regular
import anatomypro.shared.core_designsystem.generated.resources.montserrat_bold
import anatomypro.shared.core_designsystem.generated.resources.montserrat_medium
import anatomypro.shared.core_designsystem.generated.resources.montserrat_regular
import anatomypro.shared.core_designsystem.generated.resources.montserrat_semibold
import anatomypro.shared.core_designsystem.generated.resources.source_serif_4_italic
import anatomypro.shared.core_designsystem.generated.resources.source_serif_4_regular
import anatomypro.shared.core_designsystem.generated.resources.source_serif_4_semibold
import org.jetbrains.compose.resources.Font

/**
 * The prototype's three voices (spec §19).
 *
 * Latin is set in a serif and always leads; the interface speaks in a sans; anything that is
 * a label, a count or a code is monospaced and tracked out. Sizes are the prototype's, in sp
 * so they follow the system's text size (§12).
 */
class AnatomyType(sans: FontFamily, serif: FontFamily, mono: FontFamily) {
    /** A screen's question or name: "What are you studying?" */
    val title = TextStyle(fontFamily = serif, fontWeight = FontWeight.SemiBold, fontSize = 27.sp, lineHeight = 32.sp)

    /** A section's heading inside a screen. */
    val heading = TextStyle(fontFamily = serif, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 26.sp)

    /** A Latin name in a list or on a card. */
    val latin = TextStyle(fontFamily = serif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp)

    /** A figure that is the point of its card: a streak, a score. */
    val figure = TextStyle(fontFamily = serif, fontWeight = FontWeight.SemiBold, fontSize = 44.sp, lineHeight = 46.sp)

    val body = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp)
    val bodyStrong = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp)
    val caption = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp)
    val note = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp)
    val button = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp)

    /** "STEP 2 OF 3", a section's overline. */
    val label = TextStyle(fontFamily = mono, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.14.em)

    /** A badge, a count beside a name. */
    val code = TextStyle(fontFamily = mono, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 13.sp)

    /** A measured value: a size, a time, a place. */
    val value = TextStyle(fontFamily = mono, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp)

    internal val sans = sans
}

val LocalAnatomyType = staticCompositionLocalOf<AnatomyType> { error("AnatomyTheme is not above this composable") }

@Composable
internal fun rememberAnatomyType(): AnatomyType {
    val sans = FontFamily(
        Font(Res.font.montserrat_regular, FontWeight.Normal),
        Font(Res.font.montserrat_medium, FontWeight.Medium),
        Font(Res.font.montserrat_semibold, FontWeight.SemiBold),
        Font(Res.font.montserrat_bold, FontWeight.Bold),
    )
    val serif = FontFamily(
        Font(Res.font.source_serif_4_regular, FontWeight.Normal),
        Font(Res.font.source_serif_4_semibold, FontWeight.SemiBold),
        Font(Res.font.source_serif_4_italic, FontWeight.Normal, FontStyle.Italic),
    )
    val mono = FontFamily(
        Font(Res.font.ibm_plex_mono_regular, FontWeight.Normal),
        Font(Res.font.ibm_plex_mono_medium, FontWeight.Medium),
    )
    return AnatomyType(sans, serif, mono)
}

/** Material's own styles in the interface's sans, so a screen not yet restyled is at least in the right voice. */
internal fun materialTypography(sans: FontFamily): Typography {
    val base = Typography()
    fun TextStyle.inSans() = copy(fontFamily = sans)
    return Typography(
        displayLarge = base.displayLarge.inSans(), displayMedium = base.displayMedium.inSans(), displaySmall = base.displaySmall.inSans(),
        headlineLarge = base.headlineLarge.inSans(), headlineMedium = base.headlineMedium.inSans(), headlineSmall = base.headlineSmall.inSans(),
        titleLarge = base.titleLarge.inSans(), titleMedium = base.titleMedium.inSans(), titleSmall = base.titleSmall.inSans(),
        bodyLarge = base.bodyLarge.inSans(), bodyMedium = base.bodyMedium.inSans(), bodySmall = base.bodySmall.inSans(),
        labelLarge = base.labelLarge.inSans(), labelMedium = base.labelMedium.inSans(), labelSmall = base.labelSmall.inSans(),
    )
}
