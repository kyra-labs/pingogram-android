package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.WallpaperSeedRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** A [WallpaperSeedRepository] in memory: [seeds] by background id; [failure], when set, fails every write. */
class FakeWallpaperSeedRepository(
    seeds: Map<Long, Int> = emptyMap(),
) : WallpaperSeedRepository {
    val seeds = MutableStateFlow(seeds)

    var failure: DomainError? = null

    override fun seed(wallpaperId: Long): Flow<Int?> = seeds.map { it[wallpaperId] }.distinctUntilChanged()

    override suspend fun setSeed(
        wallpaperId: Long,
        argb: Int,
    ): Outcome<Unit> {
        failure?.let { return Outcome.Failure(it) }
        seeds.update { it + (wallpaperId to argb) }
        return Outcome.Success(Unit)
    }
}
