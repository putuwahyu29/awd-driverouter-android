package com.awd.driverouter.ui.screens.accounts

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.awd.driverouter.R
import com.awd.driverouter.data.local.*
import com.awd.driverouter.data.remote.BoxAuthManager
import com.awd.driverouter.data.remote.DropboxAuthManager
import com.awd.driverouter.data.remote.GoogleAuthManager
import com.awd.driverouter.data.remote.OneDriveAuthManager
import com.awd.driverouter.domain.model.CloudAccount
import com.awd.driverouter.domain.repository.CloudRepository
import com.awd.driverouter.util.SignatureHelper
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val googleAuthManager: GoogleAuthManager,
    private val oneDriveAuthManager: OneDriveAuthManager,
    private val dropboxAuthManager: DropboxAuthManager,
    private val boxAuthManager: BoxAuthManager,
    private val credentialManager: CredentialManager,
    private val settingsManager: SettingsManager,
    private val accountDao: AccountDao,
    private val repository: CloudRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val accounts: StateFlow<List<CloudAccount>> = accountDao.getAllAccounts()
        .map { entities -> entities.map { it.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _statusMessage = MutableSharedFlow<String>()
    val statusMessage: SharedFlow<String> = _statusMessage

    private val _isLoggingIn = MutableStateFlow(false)
    val isLoggingIn: StateFlow<Boolean> = _isLoggingIn.asStateFlow()

    val appTheme: StateFlow<AppTheme> = settingsManager.theme
    val allocationStrategy: StateFlow<AllocationStrategy> = settingsManager.strategy

    val totalSpace: StateFlow<Long> = accounts
        .map { list -> list.sumOf { it.totalSpace } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val usedSpace: StateFlow<Long> = accounts
        .map { list -> list.sumOf { it.usedSpace } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val storagePercentage: StateFlow<Float> = combine(usedSpace, totalSpace) { used, total ->
        if (total > 0) used.toFloat() / total else 0f
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    private val _providerValidation = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val providerValidation: StateFlow<Map<String, Boolean>> = _providerValidation.asStateFlow()

    fun setTheme(theme: AppTheme) {
        settingsManager.setTheme(theme)
    }

    fun setAllocationStrategy(strategy: AllocationStrategy) {
        settingsManager.setStrategy(strategy)
    }

    private fun updateValidation() {
        val validation = mapOf(
            "google" to getCredential(CredentialManager.GOOGLE_CLIENT_ID).isNotEmpty(),
            "onedrive" to (getCredential(CredentialManager.ONEDRIVE_CLIENT_ID).isNotEmpty() && 
                           getCredential(CredentialManager.ONEDRIVE_REDIRECT_URI).isNotEmpty()),
            "dropbox" to getCredential(CredentialManager.DROPBOX_APP_KEY).isNotEmpty(),
            "box" to (getCredential(CredentialManager.BOX_CLIENT_ID).isNotEmpty() && 
                      getCredential(CredentialManager.BOX_CLIENT_SECRET).isNotEmpty()),
            "webdav" to (getCredential(CredentialManager.WEBDAV_URL).isNotEmpty() &&
                         getCredential(CredentialManager.WEBDAV_USER).isNotEmpty() &&
                         getCredential(CredentialManager.WEBDAV_PASS).isNotEmpty()),
            "sftp" to (getCredential(CredentialManager.SFTP_HOST).isNotEmpty() &&
                       getCredential(CredentialManager.SFTP_USER).isNotEmpty() &&
                       getCredential(CredentialManager.SFTP_PASS).isNotEmpty())
        )
        _providerValidation.value = validation
    }

    fun saveCredential(key: String, value: String) {
        credentialManager.saveCredential(key, value)
        updateValidation()
    }

    fun getCredential(key: String): String {
        return credentialManager.getCredential(key)
    }

    fun getPackageName(): String = context.packageName

    fun getAppSHA1(): String = SignatureHelper.getSHA1(context)

    fun getAppSHA1Base64(): String = SignatureHelper.getSHA1Base64(context)

    fun getMSALSignatureHash(): String = SignatureHelper.getMSALSignatureHash(context)

    fun startGoogleSignIn(onReady: (Intent) -> Unit) {
        _isLoggingIn.value = true
        // Force sign out to ensure account picker appears for multi-account support
        googleAuthManager.signOut {
            onReady(googleAuthManager.getSignInIntent())
        }
    }

    fun onGoogleSignInResult(account: GoogleSignInAccount?, errorCode: Int? = null, errorMsg: String? = null) {
        _isLoggingIn.value = false
        if (account != null) {
            viewModelScope.launch {
                val entity = AccountEntity(
                    id = account.id ?: account.email ?: "",
                    email = account.email,
                    displayName = account.displayName,
                    providerId = "google_drive",
                    usedSpace = 0,
                    totalSpace = 0,
                    isConnected = true
                )
                accountDao.insertAccount(entity)
                _statusMessage.emit(context.getString(R.string.account_added, context.getString(R.string.google_drive)))
                repository.syncFiles(null)
            }
        } else {
            val msg = when {
                errorCode == 10 -> context.getString(R.string.error_google_10)
                errorCode == 7 -> context.getString(R.string.error_google_7)
                errorCode == 12501 -> context.getString(R.string.sign_in_cancelled)
                errorCode != null -> context.getString(R.string.error_google_unknown, errorCode.toString())
                errorMsg != null -> errorMsg
                else -> context.getString(R.string.sign_in_cancelled)
            }
            viewModelScope.launch { _statusMessage.emit(msg) }
        }
    }

    fun removeAccount(account: CloudAccount) {
        viewModelScope.launch {
            accountDao.deleteAccount(AccountEntity(account.id, account.email, account.name, account.provider, account.usedSpace, account.totalSpace, account.isConnected, account.isMainAccount))
            if (account.provider == "google_drive") googleAuthManager.signOut {}
            _statusMessage.emit(context.getString(R.string.account_removed))
        }
    }

    fun setMainAccount(accountId: String) {
        viewModelScope.launch {
            accountDao.setMainAccount(accountId)
            _statusMessage.emit(context.getString(R.string.main_account_changed))
        }
    }

    fun oneDriveSignIn(activity: Activity) {
        viewModelScope.launch {
            _isLoggingIn.value = true
            oneDriveAuthManager.signIn(activity).onSuccess { account ->
                val entity = AccountEntity(
                    id = account.id,
                    email = account.username,
                    displayName = account.username,
                    providerId = "onedrive",
                    usedSpace = 0,
                    totalSpace = 0,
                    isConnected = true
                )
                accountDao.insertAccount(entity)
                _statusMessage.emit(context.getString(R.string.account_added, context.getString(R.string.onedrive)))
                repository.syncFiles(null)
            }.onFailure {
                _statusMessage.emit(context.getString(R.string.login_failed_provider, context.getString(R.string.onedrive)))
            }
            _isLoggingIn.value = false
        }
    }

    fun dropboxSignIn(activity: Activity) {
        dropboxAuthManager.startAuthentication(activity)
    }

    fun boxSignIn(activity: Activity) {
        _isLoggingIn.value = true
        boxAuthManager.getSession()?.authenticate(activity)?.addOnCompletedListener {
            viewModelScope.launch {
                val boxUser = boxAuthManager.getSession()?.getUser()
                if (boxUser != null) {
                    val entity = AccountEntity(
                        id = boxUser.id,
                        email = boxUser.login,
                        displayName = boxUser.name,
                        providerId = "box",
                        usedSpace = 0,
                        totalSpace = 0,
                        isConnected = true
                    )
                    accountDao.insertAccount(entity)
                    _statusMessage.emit(context.getString(R.string.account_added, context.getString(R.string.box)))
                    repository.syncFiles(null)
                } else {
                    _statusMessage.emit(context.getString(R.string.login_failed_provider, context.getString(R.string.box)))
                }
            }
            _isLoggingIn.value = false
        } ?: run { _isLoggingIn.value = false }
    }

    fun webDavSignIn(url: String, user: String, pass: String, displayName: String) {
        viewModelScope.launch {
            val accountId = "webdav_${System.currentTimeMillis()}"
            credentialManager.saveAccountCredential(accountId, CredentialManager.WEBDAV_URL, url)
            credentialManager.saveAccountCredential(accountId, CredentialManager.WEBDAV_USER, user)
            credentialManager.saveAccountCredential(accountId, CredentialManager.WEBDAV_PASS, pass)
            
            val entity = AccountEntity(
                id = accountId,
                email = user,
                displayName = displayName.ifEmpty { "${context.getString(R.string.webdav)} ($url)" },
                providerId = "webdav",
                usedSpace = 0,
                totalSpace = 0,
                isConnected = true
            )
            accountDao.insertAccount(entity)
            _statusMessage.emit(context.getString(R.string.account_added, context.getString(R.string.webdav)))
            syncAllQuotas()
            repository.syncFiles(null)
        }
    }

    fun sftpSignIn(host: String, port: String, user: String, pass: String, displayName: String) {
        viewModelScope.launch {
            val accountId = "sftp_${System.currentTimeMillis()}"
            credentialManager.saveAccountCredential(accountId, CredentialManager.SFTP_HOST, host)
            credentialManager.saveAccountCredential(accountId, CredentialManager.SFTP_PORT, port)
            credentialManager.saveAccountCredential(accountId, CredentialManager.SFTP_USER, user)
            credentialManager.saveAccountCredential(accountId, CredentialManager.SFTP_PASS, pass)
            
            val entity = AccountEntity(
                id = accountId,
                email = user,
                displayName = displayName.ifEmpty { "${context.getString(R.string.sftp)} ($host)" },
                providerId = "sftp",
                usedSpace = 0,
                totalSpace = 0,
                isConnected = true
            )
            accountDao.insertAccount(entity)
            _statusMessage.emit(context.getString(R.string.account_added, context.getString(R.string.sftp)))
            syncAllQuotas()
            repository.syncFiles(null)
        }
    }


    init {
        refreshAccounts()
        syncAllQuotas()
        updateValidation()
    }

    fun syncAllQuotas() {
        viewModelScope.launch {
            repository.syncQuota()
        }
    }

    fun refreshAccounts() {

        // Sync accounts with auth managers if needed
    }
}
