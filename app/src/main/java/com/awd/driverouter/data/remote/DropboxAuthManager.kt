package com.awd.driverouter.data.remote

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.awd.driverouter.data.local.CredentialManager
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.http.OkHttp3Requestor
import com.dropbox.core.android.Auth
import com.dropbox.core.v2.DbxClientV2
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.TlsVersion

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

    private var pendingToken: String? = null

    // Dedicated OkHttpClient with broader TLS settings for better compatibility
    private val secureOkHttpClient: OkHttpClient by lazy {
        val modernSpec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .tlsVersions(TlsVersion.TLS_1_2, TlsVersion.TLS_1_3)
            .build()
        
        val compatibleSpec = ConnectionSpec.Builder(ConnectionSpec.COMPATIBLE_TLS)
            .tlsVersions(TlsVersion.TLS_1_0, TlsVersion.TLS_1_1, TlsVersion.TLS_1_2)
            .build()

        OkHttpClient.Builder()
            .connectionSpecs(listOf(modernSpec, compatibleSpec, ConnectionSpec.CLEARTEXT))
            .retryOnConnectionFailure(true)
            .build()
    }

    fun startAuthentication(activity: Activity) {
        val appKey = credentialManager.getCredential(CredentialManager.DROPBOX_APP_KEY)
        if (appKey.isNotEmpty()) {
            val uri = Uri.parse("https://www.dropbox.com/oauth2/authorize")
                .buildUpon()
                .appendQueryParameter("client_id", appKey)
                .appendQueryParameter("response_type", "token")
                .appendQueryParameter("redirect_uri", "awd-driverouter://dropbox-auth")
                .appendQueryParameter("scope", "account_info.read files.metadata.read files.content.read files.content.write")
                .build()
            
            val intent = Intent(Intent.ACTION_VIEW, uri)
            activity.startActivity(intent)
        } else {
            android.widget.Toast.makeText(activity, "Dropbox App Key not configured.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    suspend fun getCurrentAccount(): com.dropbox.core.v2.users.FullAccount? = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val token = pendingToken ?: getAccessToken()
        if (token == null) {
            return@withContext null
        }
        
        val client = createClient(token)
        
        try {
            client.users().currentAccount
        } catch (e: Exception) {
            null
        }
    }

    fun handleAuthRedirect(uri: Uri): String? {
        // OAuth2 token is usually in the fragment part after '#'
        val fragment = uri.fragment ?: return null
        val params = fragment.split("&")
        for (param in params) {
            val pair = param.split("=")
            if (pair.size == 2 && pair[0] == "access_token") {
                val token = pair[1]
                pendingToken = token
                return token
            }
        }
        return null
    }

    fun finalizeAuth(accountId: String) {
        pendingToken?.let { token ->
            saveToken(accountId, token)
            pendingToken = null
        }
    }

    private fun saveToken(accountId: String, token: String) {
        prefs.edit().putString("access_token_$accountId", token).commit()
    }

    @Deprecated("Use getAccessToken(accountId)")
    fun getAccessToken(): String? {
        return prefs.getString("access_token", null)
    }

    fun getAccessToken(accountId: String): String? {
        return prefs.getString("access_token_$accountId", null) ?: prefs.getString("access_token", null)
    }

    fun getClient(accountId: String): DbxClientV2? {
        val token = getAccessToken(accountId)
        if (token == null) {
            return null
        }
        return createClient(token)
    }

    private fun createClient(token: String): DbxClientV2 {
        val config = DbxRequestConfig.newBuilder("AwdDriveRouter")
            .withHttpRequestor(OkHttp3Requestor(secureOkHttpClient))
            .build()
        return DbxClientV2(config, token)
    }

    fun signOut(accountId: String) {
        prefs.edit().remove("access_token_$accountId").apply()
    }
}
