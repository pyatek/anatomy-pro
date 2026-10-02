package com.ptk.anatomypro.core.data.repository

import com.ptk.anatomypro.core.data.AnatomyDatabase
import com.ptk.anatomypro.core.data.entity.PreferenceEntity
import com.ptk.anatomypro.core.data.model.AppSettings
import com.ptk.anatomypro.core.data.model.NameDisplay
import com.ptk.anatomypro.core.data.model.decodeStudiedSystems
import com.ptk.anatomypro.core.data.model.encodeStudiedSystems
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)
}

/**
 * Settings on top of the preference table.
 *
 * An unreadable or unknown stored value falls back to the default rather than throwing: a
 * settings row written by a newer build must not make an older one refuse to start.
 */
class RoomSettingsRepository(private val database: AnatomyDatabase) : SettingsRepository {

    override val settings: Flow<AppSettings> =
        database.preferences().observeAll().map { rows -> rows.toSettings() }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        val dao = database.preferences()
        val current = dao.all().toSettings()
        val next = transform(current)
        for ((key, value) in next.toRows()) {
            if (value != current.toRows()[key]) dao.put(PreferenceEntity(key, value))
        }
    }

    private fun List<PreferenceEntity>.toSettings(): AppSettings {
        val map = associate { it.key to it.value }
        val defaults = AppSettings()
        return AppSettings(
            interfaceLocale = map[KEY_INTERFACE] ?: defaults.interfaceLocale,
            examinationLocale = map[KEY_EXAMINATION] ?: defaults.examinationLocale,
            nameDisplay = map[KEY_NAME_DISPLAY]
                ?.let { stored -> NameDisplay.entries.firstOrNull { it.name == stored } }
                ?: defaults.nameDisplay,
            quizTimerEnabled = map[KEY_TIMER].toBooleanOr(defaults.quizTimerEnabled),
            structureTreeMode = map[KEY_TREE_MODE].toBooleanOr(defaults.structureTreeMode),
            patternsNotColour = map[KEY_PATTERNS].toBooleanOr(defaults.patternsNotColour),
            onboarded = map[KEY_ONBOARDED].toBooleanOr(defaults.onboarded),
            studiedSystems = decodeStudiedSystems(map[KEY_STUDIED]),
        )
    }

    private fun AppSettings.toRows(): Map<String, String> = mapOf(
        KEY_INTERFACE to interfaceLocale,
        KEY_EXAMINATION to examinationLocale,
        KEY_NAME_DISPLAY to nameDisplay.name,
        KEY_TIMER to quizTimerEnabled.toString(),
        KEY_TREE_MODE to structureTreeMode.toString(),
        KEY_PATTERNS to patternsNotColour.toString(),
        KEY_ONBOARDED to onboarded.toString(),
        KEY_STUDIED to encodeStudiedSystems(studiedSystems),
    )

    private fun String?.toBooleanOr(fallback: Boolean) = when (this) {
        "true" -> true
        "false" -> false
        else -> fallback
    }

    private companion object {
        const val KEY_INTERFACE = "locale.interface"
        const val KEY_EXAMINATION = "locale.examination"
        const val KEY_NAME_DISPLAY = "display.names"
        const val KEY_TIMER = "quiz.timer"
        const val KEY_TREE_MODE = "a11y.treeMode"
        const val KEY_PATTERNS = "a11y.patterns"
        const val KEY_ONBOARDED = "onboarded"
        const val KEY_STUDIED = "study.systems"
    }
}
