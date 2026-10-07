package com.ptk.anatomypro

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ptk.anatomypro.core.data.AppDependencies
import com.ptk.anatomypro.core.data.fake.AtlasQuizSource
import com.ptk.anatomypro.core.data.fake.FakeQuizRepository
import com.ptk.anatomypro.core.data.fake.fakeAppDependencies

/**
 * Debug builds run on fakes for everything except the atlas.
 *
 * The atlas is the real one, Room over the bundled pack, because the 3D canvas always draws
 * that pack: a fake atlas beside it would show a tree that does not match the model, and
 * picking a structure on the model would select nothing, since the pack's ids are not in the
 * fixture. The fake atlas stays what feature tests use.
 */
@Composable
internal fun appDependencies(): AppDependencies {
    val atlas = rememberAtlas()
    val fakes = remember { fakeAppDependencies() }
    return remember(atlas, fakes) {
        val installed = atlas?.repository
        // The quiz asks about the atlas on screen, for the same reason the atlas itself is
        // real here: fixture ids are not in the pack, so nothing the quiz highlighted could
        // be seen and nothing tapped could be right. A harness — it skips §7's
        // verified-only rule, which would leave an unreviewed atlas with nothing to ask.
        fakes.copy(
            atlas = installed,
            quiz = installed?.let { FakeQuizRepository(source = AtlasQuizSource(it)) } ?: fakes.quiz,
        )
    }
}
