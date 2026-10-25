package dev.kyralabs.pingogram.push

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.domain.repository.PushTokenSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * The `gms` flavor's push service, Firebase Cloud Messaging (`docs/adr/0004-push-delivery.md`). Its automatic start is
 * off (`firebase_messaging_auto_init_enabled` in the flavor's manifest), so Firebase makes no token, and talks to
 * Google, until a logged-in account asks for one here.
 *
 * Firebase starts at the first call ([FirebaseStart]). Neither call throws: without Play services, without
 * `google-services.json`'s configuration or without a connection Firebase fails, which is logged (never the token)
 * and dropped. The next start asks again.
 */
class FirebasePushTokenSource
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : PushTokenSource {
        override suspend fun token(): String? = firebase("get the push token") { it.token.await() }

        override suspend fun deleteToken() {
            firebase("delete the push token") { it.deleteToken().await() }
        }

        @Suppress("TooGenericExceptionCaught") // Firebase and Play services fail with whatever their tasks report.
        private suspend fun <T> firebase(
            what: String,
            block: suspend (FirebaseMessaging) -> T,
        ): T? =
            try {
                FirebaseStart.ensure(context)
                block(FirebaseMessaging.getInstance())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not $what: ${e.javaClass.simpleName}")
                null
            }

        private companion object {
            const val TAG = "Push"
        }
    }
