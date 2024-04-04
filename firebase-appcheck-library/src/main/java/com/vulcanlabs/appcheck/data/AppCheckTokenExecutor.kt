package com.vulcanlabs.appcheck.data

import com.vulcanlabs.appcheck.domain.entity.AppCheckResult

interface AppCheckTokenExecutor {

    suspend fun getToken(): Result<AppCheckResult>
}
