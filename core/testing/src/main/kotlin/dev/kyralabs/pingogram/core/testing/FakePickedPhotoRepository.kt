package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.PickedPhotoRepository

/**
 * A [PickedPhotoRepository] that answers [preparePhoto] with what [onPreparePhoto] gives (by default a copy path in
 * the upload cache named after the URI) and records what was [discarded].
 */
class FakePickedPhotoRepository : PickedPhotoRepository {
    var onPreparePhoto: suspend (String) -> Outcome<String> = { uri -> Outcome.Success("/cache/uploads/$uri.jpg") }

    val prepared = mutableListOf<String>()
    val discarded = mutableListOf<String>()

    override suspend fun preparePhoto(uri: String): Outcome<String> {
        prepared += uri
        return onPreparePhoto(uri)
    }

    override fun discardPhoto(path: String) {
        discarded += path
    }
}
