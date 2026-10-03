package com.ptk.anatomypro.feature.atlas.layers

import androidx.compose.runtime.Composable
import anatomypro.shared.feature_atlas.generated.resources.Res
import anatomypro.shared.feature_atlas.generated.resources.system_cardiovascular_system
import anatomypro.shared.feature_atlas.generated.resources.system_joints
import anatomypro.shared.feature_atlas.generated.resources.system_lymphoid_organs
import anatomypro.shared.feature_atlas.generated.resources.system_muscular_system
import anatomypro.shared.feature_atlas.generated.resources.system_nervous_system_sense_organs
import anatomypro.shared.feature_atlas.generated.resources.system_regions_of_human_body
import anatomypro.shared.feature_atlas.generated.resources.system_skeletal_system
import anatomypro.shared.feature_atlas.generated.resources.system_visceral_systems
import com.ptk.anatomypro.core.model.SystemId
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

data class SystemName(val latin: String, val local: String)

private class Known(val latin: String, val local: StringResource)

private val KNOWN: Map<String, Known> = mapOf(
    "regions-of-human-body" to Known("Regiones corporis", Res.string.system_regions_of_human_body),
    "muscular-system" to Known("Systema musculare", Res.string.system_muscular_system),
    "visceral-systems" to Known("Splanchnologia", Res.string.system_visceral_systems),
    "cardiovascular-system" to Known("Systema cardiovasculare", Res.string.system_cardiovascular_system),
    "lymphoid-organs" to Known("Organa lymphoidea", Res.string.system_lymphoid_organs),
    "nervous-system-sense-organs" to Known("Systema nervosum et organa sensuum", Res.string.system_nervous_system_sense_organs),
    "joints" to Known("Juncturae", Res.string.system_joints),
    "skeletal-system" to Known("Systema skeletale", Res.string.system_skeletal_system),
)

/** A system's Latin name and its name in the interface language. Unknown ids show as themselves. */
@Composable
fun systemNames(system: SystemId): SystemName {
    val known = KNOWN[system.value] ?: return SystemName(system.value, system.value)
    return SystemName(known.latin, stringResource(known.local))
}
