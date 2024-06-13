package com.vulcanlabs.appcheck.manager

import android.content.Context
import com.google.firebase.appcheck.AppCheckToken
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.ktx.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.ktx.Firebase
import com.vulcanlabs.appcheck.data.FirebaseAppCheckTokenExecutor
import com.vulcanlabs.appcheck.domain.AppCheckTokenProviderFactory
import com.vulcanlabs.appcheck.domain.entity.AppCheckError
import com.vulcanlabs.appcheck.domain.entity.AppCheckResult
import com.vulcanlabs.appcheck.domain.entity.FirebaseRequestTokenStrategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * @author user
 */
class AppCheckManager(
    private var context: Context,
    private val listErrorTracking: Set<AppCheckError> = emptySet(),
    debug: Boolean
) {
    private var appCheck: AppCheckToken? = null
    private var keyRaw: String? = null
    private var sha1: String? = null
    private val blockedTimeAfterError = 5_000L

    private val channel = Channel<Job>(capacity = Channel.UNLIMITED).apply {
        GlobalScope.launch {
            consumeEach { it.join() }
        }
    }

    init {
        val factory = if (debug) {
            DebugAppCheckProviderFactory.getInstance()
        } else {
            PlayIntegrityAppCheckProviderFactory.getInstance()
        }
        Firebase.appCheck.installAppCheckProviderFactory(factory, true)
    }

    /*
    * làm mới token
    * */
    fun getAppCheckData(
        forceRefresh: Boolean,
        strategy: FirebaseRequestTokenStrategy = FirebaseRequestTokenStrategy.Basic(forceRefresh),
        callback: ((Result<AppCheckResult>) -> Unit)? = null
    ) {
        channel.trySend(
            CoroutineScope(Dispatchers.IO).launch(start = CoroutineStart.LAZY) {
                val appCheckTokenProvider = AppCheckTokenProviderFactory.getAppCheckTokenProvider(
                    appCheckTokenExecutor = FirebaseAppCheckTokenExecutor(
                        strategy,
                        sha1,
                        keyRaw,
                        listErrorTracking,
                        context
                    ),
                    blockedTimeAfterError = blockedTimeAfterError
                )
                val appCheckTokenResult = appCheckTokenProvider.provideAppCheckToken()
                callback?.invoke(appCheckTokenResult)
            }
        )
    }


    fun getAppCheckData(
        forceRefresh: Boolean,
        strategy: FirebaseRequestTokenStrategy = FirebaseRequestTokenStrategy.Basic(forceRefresh),
    ): Result<AppCheckResult> {
        return runBlocking {
            val appCheckTokenProvider = AppCheckTokenProviderFactory.getAppCheckTokenProvider(
                appCheckTokenExecutor = FirebaseAppCheckTokenExecutor(
                    strategy,
                    sha1,
                    keyRaw,
                    listErrorTracking,
                    context
                ),
                blockedTimeAfterError = blockedTimeAfterError
            )
            appCheckTokenProvider.provideAppCheckToken()
        }
    }

    suspend fun getAppCheckDataV2(
        forceRefresh: Boolean,
        strategy: FirebaseRequestTokenStrategy = FirebaseRequestTokenStrategy.Basic(forceRefresh)
    ): Result<AppCheckResult> = suspendCoroutine { continuation ->
        channel.trySend(
            CoroutineScope(Dispatchers.IO).launch(start = CoroutineStart.LAZY) {
                val appCheckTokenProvider = AppCheckTokenProviderFactory.getAppCheckTokenProvider(
                    appCheckTokenExecutor = FirebaseAppCheckTokenExecutor(
                        strategy,
                        sha1,
                        keyRaw,
                        context
                    ),
                    blockedTimeAfterError = blockedTimeAfterError
                )
                continuation.resume(appCheckTokenProvider.provideAppCheckToken())
            }
        )
    }

    fun firstInit(keyRaw: String? = null, sha1: String? = null) {
        this.sha1 = sha1
        this.keyRaw = keyRaw
        Firebase.appCheck.addAppCheckListener {
            appCheck = it
        }
        getAppCheckData(forceRefresh, callback = null)
    }

    fun onDestroyAppCheck() {
        Firebase.appCheck.removeAppCheckListener {
            appCheck = null
        }
    }

    companion object {
        const val forceRefresh = false
    }
}