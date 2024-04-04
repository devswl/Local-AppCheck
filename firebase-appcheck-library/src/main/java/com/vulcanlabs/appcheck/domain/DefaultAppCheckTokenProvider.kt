package com.vulcanlabs.appcheck.domain

import androidx.annotation.VisibleForTesting
import com.vulcanlabs.appcheck.data.AppCheckTokenExecutor
import com.vulcanlabs.appcheck.domain.entity.AppCheckResult
import com.vulcanlabs.appcheck.domain.entity.AppCheckState
import com.vulcanlabs.appcheck.exceptions.RetryNotAllowedException
import com.vulcanlabs.appcheck.exceptions.TokenExecutorServiceException
import com.vulcanlabs.appcheck.utils.Logger
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class DefaultAppCheckTokenProvider(
    private val appCheckTokenExecutor: AppCheckTokenExecutor,
    private val blockedTimeAfterError: Long = 0L
) : AppCheckTokenProvider {

    private var state: AppCheckState = AppCheckState.Idle
    private val mutex = Mutex()

    private val currentTimeMillis: Long
        get() = System.currentTimeMillis()

    @VisibleForTesting
    override val appCheckState: AppCheckState
        get() = state

    override suspend fun provideAppCheckToken(): Result<AppCheckResult> {
        return try {
            mutex.withLock {
                Logger.i(
                    "FirebaseAppCheck",
                    "class: [FirebaseAppCheckManager], " +
                            "action: [token request is executing], " +
                            "thread: [${Thread.currentThread().id}}]"
                )

                if (!allowRetry()) {
                    notifyStateChanged(generateRetryNotAllowedState())
                    return@withLock Result.failure(
                        RetryNotAllowedException("Retry is not allowed")
                    )
                }

                val tokenResult = appCheckTokenExecutor.getToken()
                    .onSuccess { appCheckResult ->
                        notifyStateChanged(AppCheckState.Ready(appCheckResult))
                    }.onFailure { exception ->
                        val errorState = AppCheckState.Error(
                            exception = exception,
                            timestampMillis = currentTimeMillis,
                            unblockRetryTime = calculateUnblockTime()
                        )
                        notifyStateChanged(errorState)
                    }

                val data = tokenResult.getOrNull()

                if (tokenResult.isFailure || data?.getToken().isNullOrEmpty()) {
                    return@withLock Result.failure(
                        tokenResult.exceptionOrNull()
                            ?: TokenExecutorServiceException()
                    )
                }

                Result.success(data!!)
            }
        } catch (exception: TimeoutCancellationException) {
            val errorState = AppCheckState.Error(
                exception = exception,
                timestampMillis = currentTimeMillis,
                unblockRetryTime = calculateUnblockTime()
            )
            notifyStateChanged(errorState)
            Result.failure(exception)
        }
    }

    private fun allowRetry(): Boolean {
        return when (val currentState = state) {
            is AppCheckState.Error -> {
                currentTimeMillis >= currentState.unblockRetryTime
            }

            is AppCheckState.RetryNotAllowed -> {
                currentTimeMillis >= currentState.unblockRetryTime
            }

            else -> {
                true
            }
        }
    }

    private fun generateRetryNotAllowedState(): AppCheckState.RetryNotAllowed {
        return when (val currentState = state) {
            is AppCheckState.Error -> {
                AppCheckState.RetryNotAllowed(
                    originalException = currentState.exception,
                    unblockRetryTime = currentState.unblockRetryTime
                )
            }

            is AppCheckState.RetryNotAllowed -> {
                currentState
            }

            else -> {
                throw IllegalStateException("Current state: [$currentState], retry should be allowed")
            }
        }
    }

    private fun calculateUnblockTime(): Long {
        return System.currentTimeMillis() + blockedTimeAfterError
    }

    private fun notifyStateChanged(state: AppCheckState) {
        this.state = state

        Logger.i(
            "FirebaseAppCheck",
            "class: [FirebaseAppCheckManager], " +
                    "action: [state changed: [$state] ], " +
                    "thread: [${Thread.currentThread().id}}]"
        )
    }
}
