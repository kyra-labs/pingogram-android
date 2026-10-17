package dev.kyralabs.pingogram.core.tdlib

import android.util.Log
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

/**
 * TDLib's JNI entry points behind a seam: `org.drinkless.tdlib.Client` loads `libtdjni` when it is first used, which
 * JVM unit tests cannot do, so they drive [DefaultTdlibClient] with a fake instead.
 */
internal interface TdlibNative {
    /** A synchronous request that needs no client, e.g. `SetLogVerbosityLevel` (`Client.execute`). */
    fun <T : TdApi.Object> execute(function: TdApi.Function<T>): TdResult<T>

    /** A new TDLib instance; [onUpdate] and the result callbacks run on TDLib's thread. */
    fun createClient(
        onUpdate: (TdApi.Object) -> Unit,
        onException: (Throwable) -> Unit,
    ): NativeClient
}

/** One TDLib instance (`Client`). */
internal fun interface NativeClient {
    fun send(
        function: TdApi.Function<*>,
        onResult: (TdApi.Object) -> Unit,
    )
}

internal object JniTdlib : TdlibNative {
    override fun <T : TdApi.Object> execute(function: TdApi.Function<T>): TdResult<T> =
        try {
            TdResult.Success(Client.execute(function))
        } catch (e: Client.ExecutionException) {
            TdResult.Failure(TdError(e.error.code, e.error.message))
        }

    override fun createClient(
        onUpdate: (TdApi.Object) -> Unit,
        onException: (Throwable) -> Unit,
    ): NativeClient {
        val client = Client.create(onUpdate, onException, onException)
        return NativeClient { function, onResult -> client.send(function, onResult) }
    }
}

/** Logging seam (android.util.Log is not available in JVM unit tests). Never pass TdApi objects or user data. */
internal interface TdlibLog {
    fun debug(message: () -> String)

    fun warn(
        message: String,
        error: Throwable? = null,
    )
}

internal class AndroidTdlibLog(
    private val isDebug: Boolean,
) : TdlibLog {
    override fun debug(message: () -> String) {
        if (isDebug) Log.d(TAG, message())
    }

    override fun warn(
        message: String,
        error: Throwable?,
    ) {
        Log.w(TAG, message, error)
    }

    private companion object {
        const val TAG = "Tdlib"
    }
}
