package com.ptk.anatomypro.core.data.fake

import com.ptk.anatomypro.core.data.model.PackFailure
import com.ptk.anatomypro.core.data.model.PackState
import com.ptk.anatomypro.core.data.model.PackStatus
import com.ptk.anatomypro.core.data.repository.PackRepository
import com.ptk.anatomypro.core.model.PackId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.yield

/**
 * Screens 03 and 19.
 *
 * [download] steps through progress before settling, so screen 03's progress bar has
 * something to draw. The steps are emissions rather than sleeps: a test should not have to
 * wait out a fake download, and a FakeBehaviour delay is how you make it slow on purpose.
 * Each step yields, because a StateFlow keeps only its latest value and progress set in one
 * uninterrupted run would never reach a collector.
 *
 * [downloadFailure] stops a download half way, as §14's interrupted download does. Only a
 * network failure is resumable: a checksum failure means the bytes held are wrong.
 */
class FakePackRepository(
    private val behaviour: FakeBehaviour = FakeBehaviour(),
    private val downloadFailure: PackFailure? = null,
    /** How many download attempts [downloadFailure] spoils before one is allowed through. */
    private val failingAttempts: Int = Int.MAX_VALUE,
    /** Screen 03 needs the first pack absent; screens 17 and 19 want it present. */
    private val initiallyInstalled: Set<PackId> = setOf(PackId("skeletal-body")),
) : PackRepository {

    /** How many times [download] has been asked for, whatever came of it. */
    var downloadRequests: Int = 0
        private set

    private fun initialStatus(id: PackId): PackStatus =
        if (id in initiallyInstalled) PackStatus.Installed else PackStatus.Available

    private val _packs = MutableStateFlow(
        listOf(
            PackState(
                id = PackId("skeletal-body"),
                version = 1,
                label = "Układ kostny",
                byteSize = 48_300_000,
                checksum = "sha256:2f1c0a",
                entitlement = null,
                status = initialStatus(PackId("skeletal-body")),
            ),
            PackState(
                id = PackId("muscular-body"),
                version = 1,
                label = "Układ mięśniowy",
                byteSize = 112_700_000,
                checksum = "sha256:9b4e17",
                entitlement = null,
                status = initialStatus(PackId("muscular-body")),
            ),
            PackState(
                id = PackId("nervous-body"),
                version = 1,
                label = "Układ nerwowy",
                byteSize = 67_400_000,
                checksum = "sha256:c30d82",
                entitlement = null,
                status = initialStatus(PackId("nervous-body")),
            ),
        ),
    )

    override val packs: Flow<List<PackState>> = _packs.asStateFlow()

    private fun setStatus(id: PackId, status: PackStatus) {
        _packs.value = _packs.value.map { if (it.id == id) it.copy(status = status) else it }
    }

    override suspend fun download(id: PackId) {
        downloadRequests++
        val spoiled = downloadFailure != null && downloadRequests <= failingAttempts
        behaviour.respond {
            val pack = _packs.value.first { it.id == id }
            setStatus(id, PackStatus.Queued)
            yield()
            for (step in 1..DOWNLOAD_STEPS) {
                if (spoiled && step > DOWNLOAD_STEPS / 2) {
                    val reason = downloadFailure!!
                    setStatus(id, PackStatus.Failed(reason, resumable = reason == PackFailure.NETWORK))
                    return@respond
                }
                setStatus(id, PackStatus.Downloading(pack.byteSize * step / DOWNLOAD_STEPS, pack.byteSize))
                yield()
                // cancel() and delete() run while this loop is suspended; without this check
                // the next step would overwrite them and the download would finish anyway.
                if (_packs.value.first { it.id == id }.status !is PackStatus.Downloading) return@respond
            }
            setStatus(id, PackStatus.Installed)
        }
    }

    override suspend fun cancel(id: PackId) {
        behaviour.respond { setStatus(id, PackStatus.Available) }
    }

    override suspend fun delete(id: PackId) {
        behaviour.respond { setStatus(id, PackStatus.Available) }
    }

    private companion object {
        const val DOWNLOAD_STEPS = 4
    }
}
