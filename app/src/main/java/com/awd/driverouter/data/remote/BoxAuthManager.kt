package com.awd.driverouter.data.remote

import android.content.Context
import com.awd.driverouter.data.local.CredentialManager
import com.box.androidsdk.content.BoxConfig
import com.box.androidsdk.content.models.BoxSession
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BoxAuthManager @Inject constructor(
    @ApplicationContext val context: Context,
    private val credentialManager: CredentialManager
) {
    private var session: BoxSession? = null

    private fun configureBox(): Boolean {
        val clientId = credentialManager.getCredential(CredentialManager.BOX_CLIENT_ID)
        val clientSecret = credentialManager.getCredential(CredentialManager.BOX_CLIENT_SECRET)
        val redirectUri = credentialManager.getCredential(CredentialManager.BOX_REDIRECT_URI).ifEmpty { "http://localhost" }
        
        if (clientId.isEmpty() || clientSecret.isEmpty()) {
            return false
        }
        
        BoxConfig.CLIENT_ID = clientId
        BoxConfig.CLIENT_SECRET = clientSecret
        BoxConfig.REDIRECT_URL = redirectUri
        return true
    }

    fun getSession(): BoxSession? {
        if (!configureBox()) return null
        if (session == null) {
            session = BoxSession(context)
        }
        return session
    }

    fun signOut() {
        session?.logout()
        session = null
    }
}
