package com.awd.driverouter.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import com.awd.driverouter.R
import com.awd.driverouter.data.local.AppTheme
import com.awd.driverouter.data.local.SettingsManager
import com.awd.driverouter.ui.screens.MainScreen
import com.awd.driverouter.ui.theme.CloudHubTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var settingsManager: SettingsManager

    @Inject
    lateinit var dropboxAuthManager: com.awd.driverouter.data.remote.DropboxAuthManager

    @Inject
    lateinit var boxAuthManager: com.awd.driverouter.data.remote.BoxAuthManager

    @Inject
    lateinit var repository: com.awd.driverouter.domain.repository.CloudRepository

    @Inject
    lateinit var accountDao: com.awd.driverouter.data.local.AccountDao

    private var isUnlocked by mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle results if needed
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDropboxRedirect(intent)
        handleBoxRedirect(intent)
    }

    private fun handleDropboxRedirect(intent: Intent) {
        intent.data?.let { uri ->
            if (uri.scheme == "awd-driverouter" && uri.host == "dropbox-auth") {
                val token = dropboxAuthManager.handleAuthRedirect(uri)
                if (token != null) {
                    lifecycleScope.launch {
                        // Small delay to ensure preferences are synchronized
                        kotlinx.coroutines.delay(1000)
                        
                        var dbAccount: com.dropbox.core.v2.users.FullAccount? = null
                        var retryCount = 0
                        while (dbAccount == null && retryCount < 3) {
                            dbAccount = dropboxAuthManager.getCurrentAccount()
                            if (dbAccount == null) {
                                kotlinx.coroutines.delay(1500)
                                retryCount++
                            }
                        }

                        val entity = com.awd.driverouter.data.local.AccountEntity(
                            id = dbAccount?.accountId ?: "dropbox_${System.currentTimeMillis()}",
                            email = dbAccount?.email ?: "Dropbox User",
                            displayName = dbAccount?.name?.displayName ?: "Dropbox",
                            providerId = "dropbox",
                            usedSpace = 0,
                            totalSpace = 0,
                            isConnected = true
                        )
                        accountDao.insertAccount(entity)
                        dropboxAuthManager.finalizeAuth(entity.id)
                        
                        // Sync in background
                        launch { repository.syncFiles(null) }
                        launch { repository.syncQuota() }
                        
                        android.widget.Toast.makeText(this@MainActivity, getString(R.string.account_added, entity.displayName), android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun handleBoxRedirect(intent: Intent) {
        intent.data?.let { uri ->
            val code = boxAuthManager.handleAuthRedirect(uri) ?: return@let
            lifecycleScope.launch {
                try {
                    // Tukar authorization code dengan access token
                    val api = boxAuthManager.exchangeCodeForToken(code)
                    if (api != null) {
                        // Ambil info user dari Box API menggunakan connection yang baru dibuat
                        val userInfo = boxAuthManager.getCurrentUser(api)
                        val accountId = userInfo?.id ?: "box_${System.currentTimeMillis()}"
                        
                        // Simpan token menggunakan accountId yang benar
                        boxAuthManager.finalizeAuth(accountId, api)

                        val entity = com.awd.driverouter.data.local.AccountEntity(
                            id = accountId,
                            email = userInfo?.login ?: "Box User",
                            displayName = userInfo?.name ?: "Box",
                            providerId = "box",
                            usedSpace = 0,
                            totalSpace = 0,
                            isConnected = true
                        )
                        accountDao.insertAccount(entity)

                        // Sync quota & files di background
                        launch { repository.syncFiles(null) }
                        launch { repository.syncQuota() }

                        android.widget.Toast.makeText(
                            this@MainActivity,
                            getString(R.string.account_added, entity.displayName),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        android.widget.Toast.makeText(
                            this@MainActivity,
                            getString(R.string.login_failed_provider, "Box"),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Throwable) {
                    android.util.Log.e("MainActivity", "Box auth crash/error: ${e.message}", e)
                    val errorMsg = when (e) {
                        is NoClassDefFoundError, is NoSuchMethodError -> "ProGuard/R8 Error: ${e.javaClass.simpleName}"
                        else -> e.message ?: "Unknown error"
                    }
                    android.widget.Toast.makeText(
                        this@MainActivity,
                        getString(R.string.login_failed, errorMsg),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        
        handleDropboxRedirect(intent)
        handleBoxRedirect(intent)
        checkPermissions()

        setContent {
            val themeState by settingsManager.theme.collectAsState()
            val languageState by settingsManager.language.collectAsState()
            val isAppLockEnabled by settingsManager.isAppLockEnabled.collectAsState()
            
            LaunchedEffect(isAppLockEnabled) {
                if (isAppLockEnabled && !isUnlocked) {
                    showBiometricPrompt()
                } else {
                    isUnlocked = true
                }
            }

            // Handle Language Change
            LaunchedEffect(languageState.code) {
                val appLocales = LocaleListCompat.forLanguageTags(languageState.code)
                if (AppCompatDelegate.getApplicationLocales() != appLocales) {
                    AppCompatDelegate.setApplicationLocales(appLocales)
                }
            }
            
            val isDarkTheme = when (themeState) {
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
                AppTheme.SYSTEM -> isSystemInDarkTheme()
            }

            CloudHubTheme(darkTheme = isDarkTheme, dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isUnlocked) {
                        MainScreen()
                    } else {
                        // Empty surface or splash while waiting for biometric
                    }
                }
            }
        }
    }

    private fun showBiometricPrompt() {
        val biometricManager = BiometricManager.from(this)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or 
                             BiometricManager.Authenticators.BIOMETRIC_WEAK or 
                             BiometricManager.Authenticators.DEVICE_CREDENTIAL
        
        when (biometricManager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                // Biometrics are available
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                // No biometrics enrolled, will fall back to PIN/Pattern automatically
            }
            else -> {
                // Other errors, potentially device doesn't support security
            }
        }

        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    isUnlocked = true
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (isUnlocked.not()) {
                        finish()
                    }
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Kunci Aplikasi")
            .setSubtitle("Gunakan sidik jari, wajah, atau PIN perangkat")
            .setAllowedAuthenticators(authenticators)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val toRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (toRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(toRequest.toTypedArray())
        }
    }
}
