package com.vulcanlabs.appcheck.domain.entity

sealed class AppCheckState {

    object Idle : AppCheckState() {

        override fun toString(): String {
            return "Idle"
        }
    }

    class Ready(
        val data: AppCheckResult
    ) : AppCheckState() {

        override fun toString(): String {
            return "Ready [${data.getToken()}]"
        }
    }

    class Error(
        val exception: Throwable,
        val timestampMillis: Long,
        val unblockRetryTime: Long
    ) : AppCheckState() {

        override fun toString(): String {
            return "Error: [${exception.message}]"
        }
    }

    class RetryNotAllowed(
        val originalException: Throwable,
        val unblockRetryTime: Long
    ) : AppCheckState() {

        override fun toString(): String {
            return "RetryNotAllowed: " +
                "error: [${originalException.message}], " +
                "current time: [${System.currentTimeMillis()}], " +
                "unblock time: [$unblockRetryTime]" +
                "left time in block: [${System.currentTimeMillis() - unblockRetryTime}]"
        }
    }
}
