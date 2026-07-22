package com.awd.driverouter.data.remote

import android.app.Activity
import android.content.Context
import android.util.Log
import com.awd.driverouter.data.local.CredentialManager
import com.microsoft.identity.client.*
import com.microsoft.identity.client.exception.MsalException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class OneDriveAuthManager @Inject constructor(
    @ApplicationContext val context: Context,
    private val credentialManager: CredentialManager
) {
    private var mMultipleAccountApp: IMultipleAccountPublicClientApplication? = null
    private val scopes = arrayOf("Files.ReadWrite.All", "User.Read")

    private suspend fun getApp(): IMultipleAccountPublicClientApplication? = withContext(Dispatchers.IO) {
        if (mMultipleAccountApp != null) {
            return@withContext mMultipleAccountApp
        }

        val clientId = credentialManager.getCredential(CredentialManager.ONEDRIVE_CLIENT_ID)
        if (clientId.isEmpty()) {
            Log.e("OneDriveAuth", "Client ID is empty!")
            return@withContext null
        }
        
        val redirectUri = credentialManager.getCredential(CredentialManager.ONEDRIVE_REDIRECT_URI)
        Log.d("OneDriveAuth", "Initializing MSAL with ClientID: ${clientId.take(5)}... and RedirectURI: $redirectUri")

        val configFile = File(context.cacheDir, "msal_config_multi.json")
        val configJson = """
            {
              "client_id" : "$clientId",
              "authorization_user_agent" : "DEFAULT",
              "redirect_uri" : "$redirectUri",
              "account_mode" : "MULTIPLE",
              "authorities" : [
                {
                  "type": "AAD",
                  "audience": {
                    "type": "AzureADandPersonalMicrosoftAccount"
                  }
                }
              ]
            }
        """.trimIndent()
        
        configFile.writeText(configJson)

        suspendCancellableCoroutine { continuation ->
            PublicClientApplication.createMultipleAccountPublicClientApplication(
                context,
                configFile,
                object : IPublicClientApplication.IMultipleAccountApplicationCreatedListener {
                    override fun onCreated(application: IMultipleAccountPublicClientApplication) {
                        Log.d("OneDriveAuth", "MSAL Application created successfully")
                        mMultipleAccountApp = application
                        continuation.resume(application)
                    }

                    override fun onError(exception: MsalException) {
                        Log.e("OneDriveAuth", "MSAL creation error: ${exception.message}", exception)
                        exception.printStackTrace()
                        continuation.resume(null)
                    }
                })
        }
    }

    suspend fun signIn(activity: Activity): Result<IAccount> {
        val app = getApp() ?: return Result.failure(Exception("OneDrive not configured"))
        
        return suspendCancellableCoroutine { continuation ->
            app.acquireToken(activity, scopes, object : AuthenticationCallback {
                override fun onSuccess(authenticationResult: IAuthenticationResult) {
                    continuation.resume(Result.success(authenticationResult.account))
                }

                override fun onError(exception: MsalException) {
                    continuation.resume(Result.failure(exception))
                }

                override fun onCancel() {
                    continuation.resume(Result.failure(Exception("Cancelled")))
                }
            })
        }
    }

    suspend fun getAccessTokenForAccount(accountId: String): String? {
        val app = getApp() ?: return null
        val account = app.getAccount(accountId) ?: return null
        
        return suspendCancellableCoroutine { continuation ->
            app.acquireTokenSilentAsync(scopes, account, app.configuration.defaultAuthority.authorityURL.toString(), object : SilentAuthenticationCallback {
                override fun onSuccess(authenticationResult: IAuthenticationResult) {
                    continuation.resume(authenticationResult.accessToken)
                }

                override fun onError(exception: MsalException) {
                    continuation.resume(null)
                }
            })
        }
    }

    suspend fun signOut(accountId: String, onComplete: () -> Unit) {
        val app = getApp()
        if (app != null) {
            val account = app.getAccount(accountId)
            if (account != null) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    app.removeAccount(account, object : IMultipleAccountPublicClientApplication.RemoveAccountCallback {
                        override fun onRemoved() {
                            onComplete()
                            continuation.resume(Unit)
                        }

                        override fun onError(exception: MsalException) {
                            onComplete()
                            continuation.resume(Unit)
                        }
                    })
                }
            } else {
                onComplete()
            }
        } else {
            onComplete()
        }
    }
}
