package com.vulcanlabs.appcheck.domain

import androidx.annotation.VisibleForTesting
import com.vulcanlabs.appcheck.domain.entity.AppCheckResult
import com.vulcanlabs.appcheck.domain.entity.AppCheckState

interface AppCheckTokenProvider {

    @VisibleForTesting
    val appCheckState: AppCheckState

    /**
     * Method will provide app-check token in case it's available.
     * If operation call to fetch token was failed - it will return empty string
     */
    suspend fun provideAppCheckToken(): Result<AppCheckResult>
}
