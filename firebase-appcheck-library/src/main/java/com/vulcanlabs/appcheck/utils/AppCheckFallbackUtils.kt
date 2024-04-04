package com.vulcanlabs.appcheck.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import androidx.annotation.RequiresApi
import com.google.gson.GsonBuilder
import com.vulcanlabs.appcheck.domain.entity.AppCheckError
import com.vulcanlabs.appcheck.domain.entity.AppCheckPayload
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher


class AppCheckFallbackUtils private constructor() {
    private val TEXT_TYPE_RSA = "RSA"
    private val TEXT_TYPE_RSA_ECB_PKCS1Padding = "RSA/ECB/PKCS1Padding"
    private val TEXT_TYPE_SHA1 = "SHA1"
    private val TIME_EXP = 30L
    private var publicKey: String? = null
    private var appCheckError = AppCheckError.OTHERS

    /*
    * local token khi Fail AppCheck
    * */
    @RequiresApi(Build.VERSION_CODES.KITKAT)
    fun getTokenLocal(
        context: Context,
        sha1: String?,
        keyRaw: String?,
        error: AppCheckError
    ): String? {
        try {
            if (error == AppCheckError.APP_ATTESTATION_FAILED) {
                appCheckError = error
            }
            val sha1 =
                (getApplicationSignature(context) ?: sha1 ?: "").trim()
            val iat = System.currentTimeMillis()
            val data = AppCheckPayload(
                sha1,
                iat + TimeUnit.SECONDS.toMillis(TIME_EXP),
                iat
            )
            val gson = GsonBuilder().disableHtmlEscaping().create()
            return encryptString(gson.toJson(data), keyRaw)
        } catch (e: Exception) {
            e.printStackTrace()
            Logger.handleExecption(RuntimeException("AppCheckFallbackUtils Exception, just ignore!: ${e.message}"))
            return ErrorCreateTokenLocal.TOKEN_LOCAL_EXCEPTION.name
        }
    }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    private fun getApplicationSignature(context: Context): String? {
        var signatureList: List<String> = emptyList()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // New signature
                getPackageInfo(context)?.signingInfo?.let { sig ->
                    signatureList = if (sig.hasMultipleSigners()) {
                        // Send all with apkContentsSigners
                        sig.apkContentsSigners.map {
                            val digest = MessageDigest.getInstance(TEXT_TYPE_SHA1)
                            digest.update(it.toByteArray())
                            Base64.encode(
                                digest.digest(),
                                Base64.DEFAULT
                            ).toString(StandardCharsets.UTF_8).trim()
                        }
                    } else {
                        // Send one with signingCertificateHistory
                        sig.signingCertificateHistory.map {
                            val digest = MessageDigest.getInstance(TEXT_TYPE_SHA1)
                            digest.update(it.toByteArray())
                            Base64.encode(digest.digest(), Base64.DEFAULT)
                                .toString(StandardCharsets.UTF_8).trim()
                        }
                    }
                }
            } else {
                getPackageInfo(context)?.signatures?.let { sig ->
                    signatureList = sig.map {
                        val digest = MessageDigest.getInstance(TEXT_TYPE_SHA1)
                        digest.update(it.toByteArray())
                        Base64.encode(digest.digest(), Base64.DEFAULT)
                            .toString(StandardCharsets.UTF_8).trim()
                    }
                }
            }

            return signatureList.firstOrNull()
        } catch (e: Exception) {
            e.printStackTrace()
            Logger.handleExecption(RuntimeException("AppCheckFallbackUtils Exception, just ignore!: ${e.message}"))
        }
        return null
    }


    /*
    * mã hoá RSA
    * */
    @RequiresApi(Build.VERSION_CODES.KITKAT)
    private fun encryptString(cipherText: String, keyRaw: String?): String? {
        try {
            val cipher = Cipher.getInstance(TEXT_TYPE_RSA_ECB_PKCS1Padding)
            val key = getPublicKey(keyRaw)
            key?.let {
                cipher.init(Cipher.ENCRYPT_MODE, key)
                val decryptedText = cipher.doFinal(cipherText.toByteArray(StandardCharsets.UTF_8))
                return Base64.encodeToString(decryptedText, Base64.NO_WRAP)
            }
            return ErrorCreateTokenLocal.NO_PUB_KEY.name
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
            Logger.handleExecption(RuntimeException("AppCheckFallbackUtils Exception, just ignore!: ${e.message}"))
            return ErrorCreateTokenLocal.NO_ENCRYPT.name
        }
    }

    @RequiresApi(Build.VERSION_CODES.FROYO)
    private fun getPublicKey(raw: String?): PublicKey? {
        publicKey = publicKey ?: raw
        return try {
            val keySpec = X509EncodedKeySpec(
                Base64.decode(
                    publicKey?.toByteArray(),
                    Base64.DEFAULT
                )
            )
            val kf = KeyFactory.getInstance(TEXT_TYPE_RSA)
            kf.generatePublic(keySpec)
        } catch (e: Exception) {
            e.printStackTrace()
            Logger.handleExecption(RuntimeException("AppCheckFallbackUtils Exception, just ignore!: ${e.message}"))
            null
        }
    }

    fun getAppCheckError(error: String?): AppCheckError {
        val errorMessage = error?.trim() ?: ""
        if (errorMessage.contains(AppCheckError.APP_ATTESTATION_FAILED.value))
            return AppCheckError.APP_ATTESTATION_FAILED
        return AppCheckError.OTHERS
    }

    private fun getPackageInfo(context: Context): PackageInfo? {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                val flags =
                    PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_PERMISSIONS
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(flags.toLong()),
                )
            }

            else -> {
                val flags = PackageManager.GET_SIGNATURES or PackageManager.GET_PERMISSIONS
                context.packageManager.getPackageInfo(
                    context.packageName,
                    flags,
                )
            }
        }
    }

    fun getInstance() {
        INSTANCE ?: run { INSTANCE = AppCheckFallbackUtils() }
    }

    fun getErrorAppCheck(): AppCheckError {
        return appCheckError
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var INSTANCE: AppCheckFallbackUtils? = null
        fun getInstance(): AppCheckFallbackUtils =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppCheckFallbackUtils().also { INSTANCE = it }
            }
    }
}

enum class ErrorCreateTokenLocal {
    NO_ENCRYPT,
    NO_PUB_KEY,
    TOKEN_LOCAL_EXCEPTION
}