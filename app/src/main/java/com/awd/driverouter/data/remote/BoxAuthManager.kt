package com.awd.driverouter.data.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.awd.driverouter.data.local.CredentialManager
import com.box.sdk.BoxAPIConnection
import com.box.sdk.BoxAPIConnectionListener
import com.box.sdk.BoxAPIException
import com.box.sdk.BoxUser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BoxAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val credentialManager: CredentialManager
) {
    companion object {
        private const val PREF_FILE = "box_secure_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        const val REDIRECT_URI = "awd-driverouter://box-auth"

        private const val BOX_AUTH_URL = "https://account.box.com/api/oauth2/authorize"
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREF_FILE,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // Menyimpan kode otorisasi sementara sebelum ditukar dengan token
    private var pendingAuthCode: String? = null

    /**
     * Membuka halaman login Box via Custom Tabs atau browser biasa.
     * Membutuhkan CLIENT_ID dan CLIENT_SECRET yang sudah dikonfigurasi.
     */
    fun startAuthentication(context: Context) {
        val clientId = credentialManager.getCredential(CredentialManager.BOX_CLIENT_ID)
        val clientSecret = credentialManager.getCredential(CredentialManager.BOX_CLIENT_SECRET)

        if (clientId.isEmpty() || clientSecret.isEmpty()) {
            android.widget.Toast.makeText(
                context,
                context.getString(com.awd.driverouter.R.string.provider_not_configured, "Box"),
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Bangun URL otorisasi OAuth2 Box
        val authUri = Uri.parse(BOX_AUTH_URL)
            .buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .build()

        // Coba buka via Custom Tabs untuk pengalaman yang lebih mulus
        try {
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            customTabsIntent.launchUrl(context, authUri)
        } catch (e: Exception) {
            // Fallback ke browser biasa jika Custom Tabs tidak tersedia
            val browserIntent = Intent(Intent.ACTION_VIEW, authUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
        }
    }

    /**
     * Memproses URI redirect dari Box setelah user login.
     * Format URI: awd-driverouter://box-auth?code=AUTH_CODE
     *
     * @return authorization code jika berhasil, null jika gagal
     */
    fun handleAuthRedirect(uri: Uri): String? {
        if (uri.scheme != "awd-driverouter" || uri.host != "box-auth") return null

        val code = uri.getQueryParameter("code")
        if (!code.isNullOrEmpty()) {
            pendingAuthCode = code
        }
        return code
    }

    /**
     * Menukar authorization code dengan access_token dan refresh_token menggunakan Box Java SDK.
     * Harus dipanggil di coroutine background (IO dispatcher).
     *
     * @param code Authorization code yang didapat dari [handleAuthRedirect]
     * @return [BoxAPIConnection] jika berhasil, null jika gagal
     */
    suspend fun exchangeCodeForToken(code: String): BoxAPIConnection? = withContext(Dispatchers.IO) {
        val clientId = credentialManager.getCredential(CredentialManager.BOX_CLIENT_ID)
        val clientSecret = credentialManager.getCredential(CredentialManager.BOX_CLIENT_SECRET)

        if (clientId.isEmpty() || clientSecret.isEmpty()) return@withContext null

        return@withContext try {
            // Box Java SDK: tukar authorization code dengan access token
            val api = BoxAPIConnection(clientId, clientSecret, code)
            api
        } catch (e: Exception) {
            android.util.Log.e("BoxAuthManager", "Gagal exchange code: ${e.message}", e)
            null
        }
    }

    /**
     * Mendapatkan informasi user Box yang sedang login.
     * Menggunakan BoxAPIConnection yang baru dibuat dari token.
     */
    suspend fun getCurrentUser(api: com.box.sdk.BoxAPIConnection): com.box.sdk.BoxUser.Info? = withContext(Dispatchers.IO) {
        return@withContext try {
            BoxUser.getCurrentUser(api).getInfo("id", "login", "name", "space_used", "space_amount")
        } catch (e: Exception) {
            android.util.Log.e("BoxAuthManager", "Gagal get user: ${e.message}", e)
            null
        }
    }

    /**
     * Mendapatkan informasi user Box menggunakan token dari akun yang tersimpan.
     */
    suspend fun getCurrentUser(accountId: String): com.box.sdk.BoxUser.Info? = withContext(Dispatchers.IO) {
        val api = getApiConnection(accountId) ?: return@withContext null
        return@withContext try {
            BoxUser.getCurrentUser(api).getInfo("id", "login", "name", "space_used", "space_amount")
        } catch (e: Exception) {
            android.util.Log.e("BoxAuthManager", "Gagal get user: ${e.message}", e)
            null
        }
    }

    /**
     * Menyimpan token ke EncryptedSharedPreferences setelah berhasil auth.
     *
     * @param accountId ID unik akun (biasanya user ID dari Box)
     * @param api BoxAPIConnection yang sudah berisi access token valid
     */
    fun finalizeAuth(accountId: String, api: BoxAPIConnection) {
        prefs.edit()
            .putString("${KEY_ACCESS_TOKEN}_$accountId", api.accessToken)
            .putString("${KEY_REFRESH_TOKEN}_$accountId", api.refreshToken)
            .apply()
        pendingAuthCode = null
    }

    /**
     * Membuat BoxAPIConnection dengan token yang tersimpan untuk akun tertentu.
     */
    fun getApiConnection(accountId: String): BoxAPIConnection? {
        val clientId = credentialManager.getCredential(CredentialManager.BOX_CLIENT_ID)
        val clientSecret = credentialManager.getCredential(CredentialManager.BOX_CLIENT_SECRET)

        if (clientId.isEmpty() || clientSecret.isEmpty()) return null

        val accessToken = prefs.getString("${KEY_ACCESS_TOKEN}_$accountId", null) ?: return null
        val refreshToken = prefs.getString("${KEY_REFRESH_TOKEN}_$accountId", null)

        return try {
            val api = if (refreshToken != null) {
                BoxAPIConnection(clientId, clientSecret, accessToken, refreshToken)
            } else {
                BoxAPIConnection(clientId, clientSecret, accessToken, null)
            }
            api.connectTimeout = 8000
            api.readTimeout = 10000
            api.maxRetryAttempts = 2
            
            api.addListener(object : BoxAPIConnectionListener {
                override fun onRefresh(connection: BoxAPIConnection) {
                    val newAccess = connection.accessToken
                    val newRefresh = connection.refreshToken
                    if (!newAccess.isNullOrEmpty()) {
                        prefs.edit().putString("${KEY_ACCESS_TOKEN}_$accountId", newAccess).apply()
                    }
                    if (!newRefresh.isNullOrEmpty()) {
                        prefs.edit().putString("${KEY_REFRESH_TOKEN}_$accountId", newRefresh).apply()
                    }
                }

                override fun onError(connection: BoxAPIConnection, error: BoxAPIException) {
                    android.util.Log.e("BoxAuthManager", "Gagal refresh token: ${error.message}", error)
                }
            })
            api
        } catch (e: Exception) {
            android.util.Log.e("BoxAuthManager", "Gagal buat API connection: ${e.message}", e)
            null
        }
    }

    /**
     * Cek apakah ada akun Box yang sedang login (berdasarkan accountId).
     */
    fun isAuthenticated(accountId: String): Boolean {
        return prefs.getString("${KEY_ACCESS_TOKEN}_$accountId", null) != null
    }

    /**
     * Logout: hapus semua token tersimpan untuk akun tertentu.
     */
    fun signOut(accountId: String) {
        prefs.edit()
            .remove("${KEY_ACCESS_TOKEN}_$accountId")
            .remove("${KEY_REFRESH_TOKEN}_$accountId")
            .apply()
    }
}
