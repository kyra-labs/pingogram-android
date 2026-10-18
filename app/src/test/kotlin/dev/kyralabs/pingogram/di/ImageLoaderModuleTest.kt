package dev.kyralabs.pingogram.di

import android.app.Application
import android.graphics.Bitmap
import coil3.ColorImage
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.decode.DataSource
import coil3.memory.MemoryCache
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AuthStep
import dev.kyralabs.pingogram.core.domain.model.DownloadPriority
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.repository.FileRepository
import dev.kyralabs.pingogram.core.ui.image.TdlibFileImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.seconds

/** The app's Coil image loader: TDLib files through the fetcher, cached in memory only, until TDLib closes. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ImageLoaderModuleTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context = RuntimeEnvironment.getApplication()

    // mainCoroutineContext
    @OptIn(ExperimentalCoilApi::class)
    @Test
    fun `loads TDLib files from disk, then from the memory cache, and never caches them on disk`() {
        val image = folder.newFile("photo.png")
        val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        image.outputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 0, output) }
        val files = DownloadedFiles(mapOf(FILE_ID to image.path))
        // Coil resumes requests on the main thread, which this test blocks under Robolectric.
        val imageLoader =
            tdlibImageLoader(context, files).newBuilder().mainCoroutineContext(Dispatchers.Unconfined).build()

        val first = imageLoader.load(FILE_ID)
        val second = imageLoader.load(FILE_ID)

        assertEquals(DataSource.DISK, first.dataSource)
        assertEquals(DataSource.MEMORY_CACHE, second.dataSource)
        assertNull(imageLoader.diskCache)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `clears the memory cache when TDLib closes, and only then`() =
        runTest {
            val authSteps = MutableSharedFlow<AuthStep>()
            val imageLoader = tdlibImageLoader(context, DownloadedFiles(emptyMap()))
            imageLoader.clearMemoryCacheWhenTdlibCloses(authSteps, backgroundScope)
            runCurrent()
            val memoryCache = checkNotNull(imageLoader.memoryCache)
            memoryCache[MemoryCache.Key("tdlib-file:$FILE_ID")] = MemoryCache.Value(ColorImage())

            authSteps.emit(AuthStep.LoggingOut)
            runCurrent()
            assertEquals(1, memoryCache.keys.size)

            authSteps.emit(AuthStep.Closed)
            runCurrent()
            assertTrue(memoryCache.keys.isEmpty())
        }

    private fun ImageLoader.load(fileId: Int): SuccessResult =
        runBlocking {
            withTimeout(10.seconds) {
                val request =
                    ImageRequest
                        .Builder(context)
                        .data(TdlibFileImage(fileId))
                        .size(SIZE_PX)
                        // Robolectric has no hardware bitmaps.
                        .allowHardware(false)
                        .build()
                val result = execute(request)
                check(result is SuccessResult) { "Not loaded: ${(result as ErrorResult).throwable}" }
                result
            }
        }

    private companion object {
        const val FILE_ID = 7
        const val SIZE_PX = 4
    }
}

/** Files that are on the device already, at the given paths. */
private class DownloadedFiles(
    private val paths: Map<Int, String>,
) : FileRepository {
    override fun observe(fileId: Int): Flow<FileState> =
        paths[fileId]?.let { path -> flowOf(FileState.Downloaded(path, size = 0)) } ?: flowOf()

    override suspend fun download(
        fileId: Int,
        priority: DownloadPriority,
    ): Outcome<Unit> = error("Already downloaded: $fileId")

    override suspend fun cancelDownload(fileId: Int): Outcome<Unit> = error("Not downloading: $fileId")
}
