package com.awd.driverouter.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest
import java.util.Locale

object SignatureHelper {

    /**
     * Mendapatkan SHA-1 fingerprint dari aplikasi.
     */
    @SuppressLint("PackageManagerGetSignatures")
    fun getSHA1(context: Context): String {
        return try {
            val packageName = context.packageName
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val packageInfo = context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
                packageInfo.signingInfo.apkContentsSigners
            } else {
                val packageInfo = context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNATURES
                )
                packageInfo.signatures
            }

            val md = MessageDigest.getInstance("SHA1")
            val digest = md.digest(signatures[0].toByteArray())
            
            digest.joinToString(":") {
                String.format("%02X", it)
            }.uppercase(Locale.getDefault())
            
        } catch (e: Exception) {
            e.printStackTrace()
            "Error retrieving SHA-1"
        }
    }

    fun getSHA1Base64(context: Context): String {
        return try {
            val packageName = context.packageName
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val packageInfo = context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
                packageInfo.signingInfo.apkContentsSigners
            } else {
                val packageInfo = context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNATURES
                )
                packageInfo.signatures
            }

            val md = MessageDigest.getInstance("SHA1")
            val digest = md.digest(signatures[0].toByteArray())
            android.util.Base64.encodeToString(digest, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            "Error"
        }
    }

    /**
     * MSAL (Azure) requires the signature hash in a specific format for the Redirect URI.
     * This is SHA-1 binary -> Base64 -> URL Encoded.
     */
    fun getMSALSignatureHash(context: Context): String {
        return try {
            val packageName = context.packageName
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val packageInfo = context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
                packageInfo.signingInfo.apkContentsSigners
            } else {
                val packageInfo = context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNATURES
                )
                packageInfo.signatures
            }

            val md = MessageDigest.getInstance("SHA1")
            val digest = md.digest(signatures[0].toByteArray())
            // Azure usually uses standard Base64 for the registration, 
            // but the Redirect URI itself might need to be URL encoded if it contains special chars.
            // However, MSAL Android library handles the scheme msauth://<pkg>/<base64>
            android.util.Base64.encodeToString(digest, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
        } catch (e: Exception) {
            e.printStackTrace()
            "Error"
        }
    }
}
