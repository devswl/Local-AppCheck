package com.vulcanlabs.appcheck.domain.entity

data class AppCheckPayload(
    var sha1: String,
    var exp: Long,
    var iat: Long
)