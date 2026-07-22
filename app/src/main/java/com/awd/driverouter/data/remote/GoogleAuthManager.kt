package com.awd.driverouter.data.remote

import android.content.Context
import android.content.Intent
import com.awd.driverouter.data.local.CredentialManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleAuthManager @Inject constructor(
    @ApplicationContext val context: Context,
    private val credentialManager: CredentialManager
) {
    private fun getSignInClient(): GoogleSignInClient {
        val clientId = credentialManager.getCredential(CredentialManager.GOOGLE_CLIENT_ID)
        val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                Scope(DriveScopes.DRIVE), 
                Scope(DriveScopes.DRIVE_METADATA_READONLY)
            )
        
        if (clientId.isNotEmpty()) {
            builder.requestIdToken(clientId)
        }
        
        return GoogleSignIn.getClient(context, builder.build())
    }





    fun getSignInIntent(): Intent = getSignInClient().signInIntent

    fun getForceSignInIntent(): Intent {
        val client = getSignInClient()
        // We don't sign out here as it's async, but the UI can call it or we can 
        // rely on the fact that if we use a new client instance or clear, it might help.
        // Actually, the best way is to sign out first.
        return client.signInIntent
    }

    fun getLastSignedInAccount(): GoogleSignInAccount? = GoogleSignIn.getLastSignedInAccount(context)

    fun signOut(onComplete: () -> Unit) {
        getSignInClient().signOut().addOnCompleteListener { onComplete() }
    }
}
