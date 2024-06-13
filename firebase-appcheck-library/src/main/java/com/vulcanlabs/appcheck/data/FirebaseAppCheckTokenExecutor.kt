package com.vulcanlabs.appcheck.data

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.google.android.gms.tasks.Task
import com.google.firebase.appcheck.AppCheckToken
import com.google.firebase.appcheck.FirebaseAppCheck
import com.vulcanlabs.appcheck.domain.entity.AppCheckError
import com.vulcanlabs.appcheck.domain.entity.AppCheckResult
import com.vulcanlabs.appcheck.domain.entity.FirebaseRequestTokenStrategy
import com.vulcanlabs.appcheck.utils.AppCheckFallbackUtils
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URLEncoder
import kotlin.coroutines.resume

class FirebaseAppCheckTokenExecutor(
    private val strategy: FirebaseRequestTokenStrategy,
    private val sha1: String?,
    private val keyRaw: String?,
    private val listErrorTracking: Set<AppCheckError>,
    private val context: Context
) : AppCheckTokenExecutor {

    private val firebaseAppCheck = FirebaseAppCheck.getInstance()
    private val mutex = Mutex()
    private val defaultError = setOf(AppCheckError.APP_ATTESTATION_FAILED)

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    override suspend fun getToken(): Result<AppCheckResult> {
        return mutex.withLock {
            executeTokenRequest(getAppCheckTokenTask(strategy))
        }
    }

    private fun getAppCheckTokenTask(strategy: FirebaseRequestTokenStrategy): Task<AppCheckToken> {
        return when (strategy) {
            is FirebaseRequestTokenStrategy.Basic -> {
                firebaseAppCheck.getAppCheckToken(strategy.refresh)
            }

            is FirebaseRequestTokenStrategy.Limited -> {
                firebaseAppCheck.limitedUseAppCheckToken
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    private suspend fun executeTokenRequest(tokenTask: Task<AppCheckToken>): Result<AppCheckResult> {
        return suspendCancellableCoroutine { continuation ->
            tokenTask.addOnSuccessListener { appCheckResult ->
                val token = appCheckResult.token

                if (!continuation.isCancelled) {
                    continuation.resume(Result.success(AppCheckResult(token)))
                }
            }.addOnFailureListener { exception ->
                if (!continuation.isCancelled) {
                    processData(context, sha1, keyRaw, continuation, exception)
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    private fun processData(
        context: Context,
        sha1: String?,
        keyRaw: String?,
        continuation: CancellableContinuation<Result<AppCheckResult>>,
        exception: Exception?
    ) {
        var errorMessage = exception?.message ?: "AppCheck failed"
        errorMessage = URLEncoder.encode(errorMessage, Charsets.UTF_8.name())
        val errorCode =
            AppCheckFallbackUtils.getInstance().getAppCheckError(exception?.message)
        val tokenFallback =
            if (!keyRaw.isNullOrEmpty()) AppCheckFallbackUtils.getInstance()
                .getTokenLocal(context, sha1, keyRaw, errorCode)
            else null
        val data = AppCheckResult(
            null,
            tokenFallback, errorMessage
        )
        val error = AppCheckFallbackUtils.getInstance().getErrorAppCheck()
        continuation.resume(
            if (!(listErrorTracking + defaultError).contains(AppCheckFallbackUtils.getInstance().getErrorAppCheck())) {
                Result.success(data)
            } else {
                Result.failure(RuntimeException(error.value))
            }
        )
    }
}
