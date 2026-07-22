package com.awd.driverouter.data.remote

import android.app.Activity
import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.awd.driverouter.data.local.CredentialManager
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.android.Auth
import com.dropbox.core.v2.DbxClientV2
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DropboxAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val credentialManager: CredentialManager
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "dropbox_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun startAuthentication(activity: Activity) {
        val appKey = credentialManager.getCredential(CredentialManager.DROPBOX_APP_KEY)
        if (appKey.isNotEmpty()) {
            Auth.startOAuth2Authentication(activity, appKey)
        }
    }

    fun getAccessToken(): String? {
        var token = prefs.getString("access_token", null)
        if (token == null) {
            token = Auth.getOAuth2Token()
            if (token != null) {
                prefs.edit().putString("access_token", token).apply()
            }
        }
        return token
    }

    fun getClient(): DbxClientV2? {
        val token = getAccessToken() ?: return null
        val config = DbxRequestConfig.newBuilder("CloudDriveHub").build()
        return DbxClientV2(config, token)
    }

    fun signOut() {
        prefs.edit().remove("access_token").apply()
    }
}
