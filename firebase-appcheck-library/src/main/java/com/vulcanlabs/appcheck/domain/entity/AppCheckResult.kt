package com.vulcanlabs.appcheck.domain.entity

class AppCheckResult(
    private var appCheckToken: String? = null,
    private var localToken: String? = null,
    private var errorMessage: String? = null
) {
    fun isAppCheck() = appCheckToken?.isNotEmpty() == true
    fun getToken() = appCheckToken ?: localToken ?: "TokenFail"
    fun getMessage() = errorMessage
}