package com.ptk.anatomypro

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ptk.anatomypro.core.model.StructureId

/** Android hosts Filament through SceneView, which is the next phase-0 task. */
@Composable
actual fun AnatomyCanvas(
    modifier: Modifier,
    onPicked: (StructureId?) -> Unit,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text("Android renderer host not implemented yet", style = MaterialTheme.typography.bodySmall)
    }
}
