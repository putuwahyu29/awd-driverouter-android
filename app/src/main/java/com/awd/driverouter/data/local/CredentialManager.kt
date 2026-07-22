package com.awd.driverouter.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CredentialManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "secure_credentials",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveCredential(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun getCredential(key: String): String {
        return prefs.getString(key, "") ?: ""
    }

    fun saveAccountCredential(accountId: String, key: String, value: String) {
        prefs.edit().putString("${accountId}_$key", value).apply()
    }

    fun getAccountCredential(accountId: String, key: String): String {
        return prefs.getString("${accountId}_$key", "") ?: ""
    }

    companion object {
        const val GOOGLE_CLIENT_ID = "google_client_id"
        const val ONEDRIVE_CLIENT_ID = "onedrive_client_id"
        const val ONEDRIVE_REDIRECT_URI = "onedrive_redirect_uri"
        const val DROPBOX_APP_KEY = "dropbox_app_key"
        const val BOX_CLIENT_ID = "box_client_id"
        const val BOX_CLIENT_SECRET = "box_client_secret"
        const val BOX_REDIRECT_URI = "box_redirect_uri"
        
        // WebDAV
        const val WEBDAV_URL = "webdav_url"
        const val WEBDAV_USER = "webdav_user"
        const val WEBDAV_PASS = "webdav_pass"
        
        // SFTP
        const val SFTP_HOST = "sftp_host"
        const val SFTP_PORT = "sftp_port"
        const val SFTP_USER = "sftp_user"
        const val SFTP_PASS = "sftp_pass"
    }
}
