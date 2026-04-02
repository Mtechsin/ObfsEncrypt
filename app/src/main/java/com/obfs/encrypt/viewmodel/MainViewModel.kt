package com.obfs.encrypt.viewmodel

import android.app.Application
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.obfs.encrypt.crypto.DecryptionResult
import com.obfs.encrypt.crypto.EncryptionHelper
import com.obfs.encrypt.crypto.EncryptionMethod
import com.obfs.encrypt.data.AppDirectoryManager
import com.obfs.encrypt.data.FileEncryptionManager
import com.obfs.encrypt.data.EncryptionHistoryItem
import com.obfs.encrypt.data.EncryptionHistoryRepository
import com.obfs.encrypt.data.SecureDelete
import com.obfs.encrypt.data.ProgressState
import com.obfs.encrypt.data.RecentFoldersRepository
import com.obfs.encrypt.data.SettingsRepository
import com.obfs.encrypt.data.createHistoryItem
import com.obfs.encrypt.data.formatFileSize
import com.obfs.encrypt.security.AppPasswordManager
import com.obfs.encrypt.security.BiometricAuthManager
import com.obfs.encrypt.services.CryptoService
import com.obfs.encrypt.ui.theme.AppTheme
import com.obfs.encrypt.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.work.WorkInfo
import androidx.work.WorkManager

@HiltViewModel
class MainViewModel @Inject constructor(
    private val application: Application,
    private val encryptionHelper: EncryptionHelper,
    private val settingsRepository: SettingsRepository,
    private val appDirectoryManager: AppDirectoryManager,
    private val historyRepository: EncryptionHistoryRepository,
    val biometricAuthManager: BiometricAuthManager,
    val appPasswordManager: AppPasswordManager,
    private val batchEncryptionManager: com.obfs.encrypt.data.BatchEncryptionManager,
    private val fileEncryptionManager: FileEncryptionManager
) : AndroidViewModel(application) {

    private val BATCH_THRESHOLD = 5
    private val workManager = WorkManager.getInstance(application)

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _progressState = MutableStateFlow(ProgressState())
    val progressState: StateFlow<ProgressState> = _progressState.asStateFlow()

    // WorkManager observation
    private val _batchWorkInfo = MutableStateFlow<List<WorkInfo>>(emptyList())
    val batchWorkInfo: StateFlow<List<WorkInfo>> = _batchWorkInfo.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _isOperationActive = MutableStateFlow(false)
    val isOperationActive: StateFlow<Boolean> = _isOperationActive.asStateFlow()

    private val _secureDeleteOriginals = MutableStateFlow(true)
    val secureDeleteOriginals: StateFlow<Boolean> = _secureDeleteOriginals.asStateFlow()

    private val _currentOutputUri = MutableStateFlow<Uri?>(null)
    val currentOutputUri: StateFlow<Uri?> = _currentOutputUri.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _appTheme = MutableStateFlow(AppTheme.DEFAULT)
    val appTheme: StateFlow<AppTheme> = _appTheme.asStateFlow()

    private val _dynamicColor = MutableStateFlow(false)
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _amoledMode = MutableStateFlow(false)
    val amoledMode: StateFlow<Boolean> = _amoledMode.asStateFlow()

    private val _quickAccessExpanded = MutableStateFlow(true)
    val quickAccessExpanded: StateFlow<Boolean> = _quickAccessExpanded.asStateFlow()

    // Keyfile support
    private val _keyfileUri = MutableStateFlow<Uri?>(null)
    val keyfileUri: StateFlow<Uri?> = _keyfileUri.asStateFlow()

    // Integrity check preference
    private val _enableIntegrityCheck = MutableStateFlow(false)
    val enableIntegrityCheck: StateFlow<Boolean> = _enableIntegrityCheck.asStateFlow()

    // Decryption result with integrity info
    private val _lastDecryptionResult = MutableStateFlow<DecryptionResult?>(null)
    val lastDecryptionResult: StateFlow<DecryptionResult?> = _lastDecryptionResult.asStateFlow()

    // App lock settings
    private val _appLockEnabled = MutableStateFlow(false)
    val appLockEnabled: StateFlow<Boolean> = _appLockEnabled.asStateFlow()

    private val _appLockTimeout = MutableStateFlow(0L)
    val appLockTimeout: StateFlow<Long> = _appLockTimeout.asStateFlow()

    // Language setting
    private val _language = MutableStateFlow("system")
    val language: StateFlow<String> = _language.asStateFlow()

    // Onboarding state
    private val _onboardingCompleted = MutableStateFlow(false)
    val onboardingCompleted: StateFlow<Boolean> = _onboardingCompleted.asStateFlow()

    // Encryption history
    private val _encryptionHistory = MutableStateFlow<List<EncryptionHistoryItem>>(emptyList())
    val encryptionHistory: StateFlow<List<EncryptionHistoryItem>> = _encryptionHistory.asStateFlow()

    // Biometric password save prompt (shown after successful encryption)
    private val _showPasswordSavePrompt = MutableStateFlow(false)
    val showPasswordSavePrompt: StateFlow<Boolean> = _showPasswordSavePrompt.asStateFlow()

    // Temporary password storage for pending save
    private var pendingPasswordToSave: CharArray? = null

    private var currentJob: Job? = null

    private val serviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                CryptoService.ACTION_BROADCAST_CANCEL -> cancelOperation()
                CryptoService.ACTION_BROADCAST_PAUSE -> togglePause()
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(CryptoService.ACTION_BROADCAST_CANCEL)
            addAction(CryptoService.ACTION_BROADCAST_PAUSE)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            application.registerReceiver(serviceReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            application.registerReceiver(serviceReceiver, filter)
        }
        
        // Theme flows
        viewModelScope.launch {
            combine(
                settingsRepository.themeMode,
                settingsRepository.appTheme,
                settingsRepository.dynamicColor,
                settingsRepository.amoledMode
            ) { themeMode, appTheme, dynamicColor, amoledMode ->
                _themeMode.value = themeMode
                _appTheme.value = appTheme
                _dynamicColor.value = dynamicColor
                _amoledMode.value = amoledMode
            }.collect {}
        }
        // Security flows
        viewModelScope.launch {
            combine(
                settingsRepository.appLockEnabled,
                settingsRepository.appLockTimeout,
                settingsRepository.secureDeleteOriginals
            ) { lockEnabled, lockTimeout, secureDelete ->
                _appLockEnabled.value = lockEnabled
                _appLockTimeout.value = lockTimeout
                _secureDeleteOriginals.value = secureDelete
            }.collect {}
        }
        // UI & locale flows
        viewModelScope.launch {
            combine(
                settingsRepository.outputDirectoryUri,
                settingsRepository.quickAccessExpanded,
                settingsRepository.language,
                settingsRepository.onboardingCompleted
            ) { uriString, quickExpanded, language, onboardingDone ->
                _currentOutputUri.value = uriString?.let { Uri.parse(it) }
                _quickAccessExpanded.value = quickExpanded
                _language.value = language
                _onboardingCompleted.value = onboardingDone
            }.collect {}
        }
        // History & WorkManager flows
        viewModelScope.launch {
            combine(
                historyRepository.historyItems,
                workManager.getWorkInfosByTagFlow("batch_operation")
            ) { historyItems, workInfos ->
                _encryptionHistory.value = historyItems
                _batchWorkInfo.value = workInfos
            }.collect {}
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            application.unregisterReceiver(serviceReceiver)
        } catch (e: Exception) {}
    }

    fun toggleSecureDelete(enabled: Boolean) {
        _secureDeleteOriginals.value = enabled
        viewModelScope.launch { settingsRepository.setSecureDeleteOriginals(enabled) }
    }

    fun setCurrentOutputDirectory(uri: Uri?) {
        _currentOutputUri.value = uri
        viewModelScope.launch { settingsRepository.setOutputDirectoryUri(uri?.toString()) }
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setAppTheme(theme: AppTheme) {
        _appTheme.value = theme
        viewModelScope.launch { settingsRepository.setAppTheme(theme) }
    }

    fun setLanguage(language: String) {
        _language.value = language
        viewModelScope.launch { settingsRepository.setLanguage(language) }
    }

    fun completeOnboarding() {
        _onboardingCompleted.value = true
        viewModelScope.launch { settingsRepository.setOnboardingCompleted(true) }
    }

    fun setDynamicColor(enabled: Boolean) {
        _dynamicColor.value = enabled
        viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }
    }

    fun setAmoledMode(enabled: Boolean) {
        _amoledMode.value = enabled
        viewModelScope.launch { settingsRepository.setAmoledMode(enabled) }
    }

    fun setQuickAccessExpanded(expanded: Boolean) {
        _quickAccessExpanded.value = expanded
        viewModelScope.launch { settingsRepository.setQuickAccessExpanded(expanded) }
    }

    fun setKeyfileUri(uri: Uri?) {
        _keyfileUri.value = uri
    }

    fun toggleIntegrityCheck(enabled: Boolean) {
        _enableIntegrityCheck.value = enabled
    }

    fun clearLastDecryptionResult() {
        _lastDecryptionResult.value = null
    }

    // App lock methods
    fun enableAppLock() {
        _appLockEnabled.value = true
        viewModelScope.launch { settingsRepository.setAppLockEnabled(true) }
    }

    fun disableAppLock() {
        _appLockEnabled.value = false
        viewModelScope.launch { settingsRepository.setAppLockEnabled(false) }
    }

    fun setAppLockTimeout(timeout: Long) {
        _appLockTimeout.value = timeout
        viewModelScope.launch { settingsRepository.setAppLockTimeout(timeout) }
    }

    fun cancelOperation() {
        currentJob?.cancel()
        _isOperationActive.value = false
        _statusMessage.value = "Operation Cancelled"
        _progress.value = 0f
        _progressState.value = ProgressState()
        _isPaused.value = false
        CryptoService.stopService(application)
    }

    fun togglePause() {
        _isPaused.value = !_isPaused.value
        _progressState.value = _progressState.value.copy(isPaused = _isPaused.value)
    }

    private fun updateProgressState(
        current: Long,
        total: Long,
        startTime: Long,
        fileName: String? = null,
        totalFiles: Int = 0,
        currentFileIndex: Int = 0,
        isIndeterminate: Boolean = false
    ) {
        val progress = if (total > 0) (current.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
        _progress.value = progress

        val currentTime = System.currentTimeMillis()
        val elapsedTimeMs = currentTime - startTime
        
        val speed = if (elapsedTimeMs > 0) {
            (current * 1000) / elapsedTimeMs
        } else 0L

        val eta = if (speed > 0 && total > current) {
            (total - current) / speed
        } else -1L

        _progressState.value = _progressState.value.copy(
            progress = progress,
            etaSeconds = eta,
            speedBytesPerSecond = speed,
            currentFile = fileName ?: _progressState.value.currentFile,
            totalFiles = totalFiles,
            currentFileIndex = currentFileIndex,
            processedBytes = current,
            totalBytes = total,
            isIndeterminate = isIndeterminate,
            isPaused = _isPaused.value
        )
        
        // Update notification
        CryptoService.updateProgress(
            context = application,
            progress = (progress * 100).toInt(),
            status = if (totalFiles > 1) "File $currentFileIndex/$totalFiles: $fileName" else fileName ?: "Processing...",
            isPaused = _isPaused.value
        )
    }

    // ─── Biometric Password Management ─────────────────────────────────────────

    /**
     * Store the encryption password with biometric protection.
     * Call this after successful encryption when user wants to save password.
     */
    fun savePasswordWithBiometric(password: CharArray): Boolean {
        return biometricAuthManager.storePasswordWithBiometric(password)
    }

    /**
     * Retrieve the stored password after biometric authentication.
     * Returns the password as CharArray, or null if not available.
     */
    fun getStoredPassword(): CharArray? {
        return biometricAuthManager.retrieveStoredPassword()
    }

    /**
     * Check if a password is stored and ready for biometric unlock.
     */
    fun hasStoredPassword(): Boolean {
        return biometricAuthManager.hasStoredPassword()
    }

    /**
     * Clear the stored password.
     */
    fun clearStoredPassword() {
        biometricAuthManager.clearStoredPassword()
    }

    /**
     * Request to save password with biometric protection after encryption.
     * Call this when encryption completes and biometric is enabled.
     */
    fun requestPasswordSave(password: CharArray) {
        // Only show prompt if biometric is enabled and password is not empty
        if (biometricAuthManager.isBiometricEnabled() && password.isNotEmpty()) {
            pendingPasswordToSave = password.copyOf()
            _showPasswordSavePrompt.value = true
        } else {
            password.fill('0')
        }
    }

    /**
     * Confirm saving the password with biometric protection.
     */
    fun confirmSavePassword(): Boolean {
        val password = pendingPasswordToSave
        return if (password != null) {
            val success = savePasswordWithBiometric(password)
            password.fill('0')
            pendingPasswordToSave = null
            _showPasswordSavePrompt.value = false
            success
        } else {
            false
        }
    }

    /**
     * Dismiss the password save prompt without saving.
     */
    fun dismissPasswordSavePrompt() {
        pendingPasswordToSave?.fill('0')
        pendingPasswordToSave = null
        _showPasswordSavePrompt.value = false
    }

    // ─── Public entry points ───────────────────────────────────────────────────

    fun encryptFiles(
        uris: List<Uri>,
        password: CharArray,
        method: EncryptionMethod = EncryptionMethod.STANDARD,
        deleteOriginal: Boolean = false,
        enableIntegrityCheck: Boolean = false
    ) {
        if (uris.isEmpty()) return
        
        if (uris.size > BATCH_THRESHOLD) {
            batchEncryptionManager.enqueueBatch(
                operation = com.obfs.encrypt.services.EncryptionWorker.OPERATION_ENCRYPT,
                uris = uris,
                password = password,
                method = method,
                deleteOriginal = deleteOriginal,
                enableIntegrity = enableIntegrityCheck
            )
            _statusMessage.value = "Batch encryption started in background..."
            return
        }

        currentJob = viewModelScope.launch(Dispatchers.IO) {
            _isOperationActive.value = true
            _progress.value = 0f
            _isPaused.value = false
            _progressState.value = ProgressState(totalFiles = uris.size)
            CryptoService.startService(application)
            try {
                // Read keyfile if set
                val keyfileBytes = _keyfileUri.value?.let { readKeyfile(it) }

                uris.forEachIndexed { index, uri ->
                    _statusMessage.value = "Encrypting file ${index + 1} of ${uris.size}..."
                    val fileName = getFileNameFromUri(uri)
                    val fileSize = getFileSizeFromUri(uri)
                    
                    _progressState.value = _progressState.value.copy(
                        currentFile = fileName,
                        currentFileIndex = index + 1
                    )

                    try {
                        processSingleEncryption(
                            uri = uri, 
                            password = password, 
                            method = method, 
                            deleteOriginal = deleteOriginal, 
                            keyfileBytes = keyfileBytes, 
                            enableIntegrityCheck = enableIntegrityCheck,
                            currentFileIndex = index + 1,
                            totalFiles = uris.size
                        )
                        // Add to history on success
                        historyRepository.addHistoryItem(
                            createHistoryItem(
                                fileName = fileName,
                                fileSize = fileSize,
                                operationType = EncryptionHistoryItem.OperationType.ENCRYPT,
                                encryptionMethod = method,
                                success = true,
                                secureDelete = deleteOriginal
                            )
                        )
                    } catch (e: Exception) {
                        // Add failed operation to history
                        historyRepository.addHistoryItem(
                            createHistoryItem(
                                fileName = fileName,
                                fileSize = fileSize,
                                operationType = EncryptionHistoryItem.OperationType.ENCRYPT,
                                encryptionMethod = method,
                                success = false,
                                errorMessage = e.localizedMessage
                            )
                        )
                        throw e
                    }
                }
                _statusMessage.value = "Encryption Complete!"
                _progress.value = 1f
                
                // Request password save if biometric is enabled
                requestPasswordSave(password)
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.localizedMessage}"
            } finally {
                password.fill('0')
                _isOperationActive.value = false
                CryptoService.stopService(application)
            }
        }
    }

    fun encryptFolderTree(
        treeUri: Uri,
        password: CharArray,
        method: EncryptionMethod = EncryptionMethod.STANDARD,
        deleteOriginal: Boolean = false,
        enableIntegrityCheck: Boolean = false
    ) {
        currentJob = viewModelScope.launch(Dispatchers.IO) {
            _isOperationActive.value = true
            _progress.value = 0f
            _isPaused.value = false
            CryptoService.startService(application)
            try {
                // Read keyfile if set
                val keyfileBytes = _keyfileUri.value?.let { readKeyfile(it) }

                val rootFolder = DocumentFile.fromTreeUri(application, treeUri)
                if (rootFolder != null && rootFolder.isDirectory) {
                    val files = mutableListOf<DocumentFile>()
                    collectDocumentFiles(rootFolder, files)
                    _progressState.value = ProgressState(totalFiles = files.size)
                    files.forEachIndexed { index, file ->
                        _statusMessage.value = "Encrypting ${file.name} (${index + 1}/${files.size})"
                        processSingleEncryption(
                            uri = file.uri, 
                            password = password, 
                            method = method, 
                            deleteOriginal = deleteOriginal, 
                            keyfileBytes = keyfileBytes, 
                            enableIntegrityCheck = enableIntegrityCheck,
                            currentFileIndex = index + 1,
                            totalFiles = files.size
                        )
                    }
                    _statusMessage.value = "Folder Encryption Complete!"
                    _progress.value = 1f
                    
                    // Request password save if biometric is enabled
                    requestPasswordSave(password)
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.localizedMessage}"
            } finally {
                password.fill('0')
                _isOperationActive.value = false
                CryptoService.stopService(application)
            }
        }
    }

    fun decryptFiles(
        uris: List<Uri>, 
        password: CharArray, 
        deleteOriginal: Boolean = false,
        verifyIntegrity: Boolean = true
    ) {
        if (uris.isEmpty()) return

        if (uris.size > BATCH_THRESHOLD) {
            batchEncryptionManager.enqueueBatch(
                operation = com.obfs.encrypt.services.EncryptionWorker.OPERATION_DECRYPT,
                uris = uris,
                password = password,
                deleteOriginal = deleteOriginal,
                enableIntegrity = verifyIntegrity
            )
            _statusMessage.value = "Batch decryption started in background..."
            return
        }

        currentJob = viewModelScope.launch(Dispatchers.IO) {
            _isOperationActive.value = true
            _progress.value = 0f
            _isPaused.value = false
            _progressState.value = ProgressState(totalFiles = uris.size)
            CryptoService.startService(application)
            try {
                // Read keyfile if set
                val keyfileBytes = _keyfileUri.value?.let { readKeyfile(it) }
                
                uris.forEachIndexed { index, uri ->
                    _statusMessage.value = "Decrypting file ${index + 1} of ${uris.size}..."
                    val fileName = getFileNameFromUri(uri)
                    _progressState.value = _progressState.value.copy(
                        currentFile = fileName,
                        currentFileIndex = index + 1
                    )
                    val result = processSingleDecryption(
                        uri = uri, 
                        password = password, 
                        deleteOriginal = deleteOriginal, 
                        keyfileBytes = keyfileBytes, 
                        verifyIntegrity = verifyIntegrity,
                        currentFileIndex = index + 1,
                        totalFiles = uris.size
                    )
                    _lastDecryptionResult.value = result
                }
                _statusMessage.value = "Decryption Complete!"
                _progress.value = 1f
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.localizedMessage}"
            } finally {
                password.fill('0')
                _isOperationActive.value = false
                CryptoService.stopService(application)
            }
        }
    }

    fun encryptDirectFiles(
        files: List<File>,
        password: CharArray,
        method: EncryptionMethod = EncryptionMethod.STANDARD,
        deleteOriginal: Boolean = false,
        enableIntegrityCheck: Boolean = false
    ) {
        if (files.isEmpty()) return
        currentJob = viewModelScope.launch(Dispatchers.IO) {
            _isOperationActive.value = true
            _progress.value = 0f
            _isPaused.value = false
            CryptoService.startService(application)
            try {
                // Read keyfile if set
                val keyfileBytes = _keyfileUri.value?.let { readKeyfile(it) }
                
                val allFiles = mutableListOf<File>()
                for (f in files) collectJavaFiles(f, allFiles)

                if (allFiles.isEmpty()) {
                    _statusMessage.value = "No files to encrypt"
                    _isOperationActive.value = false
                    CryptoService.stopService(application)
                    return@launch
                }

                _progressState.value = ProgressState(totalFiles = allFiles.size)
                allFiles.forEachIndexed { index, file ->
                    if (!file.exists()) {
                        _statusMessage.value = "File not found: ${file.name}"
                        return@forEachIndexed
                    }
                    if (!file.canRead()) {
                        _statusMessage.value = "Cannot read file: ${file.name}"
                        return@forEachIndexed
                    }
                    _statusMessage.value = "Encrypting file ${index + 1} of ${allFiles.size}..."
                    _progressState.value = _progressState.value.copy(
                        currentFile = file.name,
                        currentFileIndex = index + 1
                    )
                    processDirectEncryption(
                        file = file, 
                        password = password, 
                        method = method, 
                        deleteOriginal = deleteOriginal, 
                        keyfileBytes = keyfileBytes, 
                        enableIntegrityCheck = enableIntegrityCheck,
                        currentFileIndex = index + 1,
                        totalFiles = allFiles.size
                    )
                }
                _statusMessage.value = "Encryption Complete!"
                _progress.value = 1f
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.localizedMessage}"
                e.printStackTrace()
            } finally {
                password.fill('0')
                _isOperationActive.value = false
                CryptoService.stopService(application)
            }
        }
    }

    fun decryptDirectFiles(
        files: List<File>,
        password: CharArray,
        deleteOriginal: Boolean = false,
        verifyIntegrity: Boolean = true
    ) {
        if (files.isEmpty()) return
        currentJob = viewModelScope.launch(Dispatchers.IO) {
            _isOperationActive.value = true
            _progress.value = 0f
            _isPaused.value = false
            CryptoService.startService(application)
            try {
                // Read keyfile if set
                val keyfileBytes = _keyfileUri.value?.let { readKeyfile(it) }
                
                val allFiles = mutableListOf<File>()
                for (f in files) collectJavaFiles(f, allFiles)

                if (allFiles.isEmpty()) {
                    _statusMessage.value = "No files to decrypt"
                    _isOperationActive.value = false
                    CryptoService.stopService(application)
                    return@launch
                }

                _progressState.value = ProgressState(totalFiles = allFiles.size)
                allFiles.forEachIndexed { index, file ->
                    if (!file.exists()) {
                        _statusMessage.value = "File not found: ${file.name}"
                        return@forEachIndexed
                    }
                    if (!file.canRead()) {
                        _statusMessage.value = "Cannot read file: ${file.name}"
                        return@forEachIndexed
                    }
                    _statusMessage.value = "Decrypting file ${index + 1} of ${allFiles.size}..."
                    _progressState.value = _progressState.value.copy(
                        currentFile = file.name,
                        currentFileIndex = index + 1
                    )
                    val result = processDirectDecryption(
                        file = file, 
                        password = password, 
                        deleteOriginal = deleteOriginal, 
                        keyfileBytes = keyfileBytes, 
                        verifyIntegrity = verifyIntegrity,
                        currentFileIndex = index + 1,
                        totalFiles = allFiles.size
                    )
                    _lastDecryptionResult.value = result
                }
                _statusMessage.value = "Decryption Complete!"
                _progress.value = 1f
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.localizedMessage}"
                e.printStackTrace()
            } finally {
                password.fill('0')
                _isOperationActive.value = false
                CryptoService.stopService(application)
            }
        }
    }
    
    /**
     * Generate and save a new keyfile.
     */
    fun generateKeyfile(outputUri: Uri, size: Int = 256) {
        currentJob = viewModelScope.launch(Dispatchers.IO) {
            _isOperationActive.value = true
            _statusMessage.value = "Generating keyfile..."
            try {
                val keyfileBytes = encryptionHelper.generateKeyfile(size)
                
                val app = getApplication<Application>()
                val cr = app.contentResolver
                
                val outputDir = DocumentFile.fromTreeUri(app, outputUri)
                    ?: throw IllegalStateException("Output directory not accessible")
                val targetFile = outputDir.createFile("application/octet-stream", "keyfile_${System.currentTimeMillis()}.key")
                    ?: throw IllegalStateException("Cannot create keyfile")
                
                cr.openOutputStream(targetFile.uri)?.use { outputStream ->
                    outputStream.write(keyfileBytes)
                    outputStream.flush()
                }
                
                // Clear sensitive data
                keyfileBytes.fill(0)
                
                _statusMessage.value = "Keyfile generated successfully!"
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.localizedMessage}"
            } finally {
                _isOperationActive.value = false
            }
        }
    }

    // ─── Private helpers ───────────────────────────────────────────────────────
    
    private suspend fun readKeyfile(uri: Uri): ByteArray {
        val app = getApplication<Application>()
        return encryptionHelper.readKeyfile(app.contentResolver, uri)
    }

    /**
     * Encrypt a SAF-picked file (Uri).
     */
    private suspend fun processSingleEncryption(
        uri: Uri, 
        password: CharArray, 
        method: EncryptionMethod = EncryptionMethod.STANDARD, 
        deleteOriginal: Boolean = false,
        keyfileBytes: ByteArray? = null,
        enableIntegrityCheck: Boolean = false,
        currentFileIndex: Int = 0,
        totalFiles: Int = 0
    ) {
        val app = getApplication<Application>()
        val fileName = getFileNameFromUri(uri)
        val sourceFile = DocumentFile.fromSingleUri(app, uri)
            ?: throw IllegalArgumentException("Could not read source file")

        val outputUri = fileEncryptionManager.createOutputFile(
            inputUri = uri,
            encrypt = true,
            customOutputDirUri = _currentOutputUri.value
        )

        fileEncryptionManager.encryptUri(
            sourceUri = uri,
            outputUri = outputUri,
            password = password,
            method = method,
            enableIntegrityCheck = enableIntegrityCheck,
            keyfileBytes = keyfileBytes,
            isPaused = _isPaused.asStateFlow(),
            progressCallback = { current, total, startTime ->
                updateProgressState(
                    current = current,
                    total = total,
                    startTime = startTime,
                    fileName = fileName,
                    currentFileIndex = currentFileIndex,
                    totalFiles = totalFiles
                )
            }
        )

        if (deleteOriginal) {
            _statusMessage.value = "Securely shredding original..."
            SecureDelete.secureDelete(app, sourceFile)
        }
    }

    /**
     * Decrypt a SAF-picked .obfs file back to plaintext.
     */
    private suspend fun processSingleDecryption(
        uri: Uri, 
        password: CharArray, 
        deleteOriginal: Boolean = false,
        keyfileBytes: ByteArray? = null,
        verifyIntegrity: Boolean = true,
        currentFileIndex: Int = 0,
        totalFiles: Int = 0
    ): DecryptionResult {
        val app = getApplication<Application>()
        val fileName = getFileNameFromUri(uri)
        val sourceFile = DocumentFile.fromSingleUri(app, uri) 
            ?: throw IllegalArgumentException("Cannot read source file")

        val outputUri = fileEncryptionManager.createOutputFile(
            inputUri = uri,
            encrypt = false,
            customOutputDirUri = _currentOutputUri.value
        )

        val result = fileEncryptionManager.decryptUri(
            sourceUri = uri,
            outputUri = outputUri,
            password = password,
            verifyIntegrity = verifyIntegrity,
            keyfileBytes = keyfileBytes,
            isPaused = _isPaused.asStateFlow(),
            progressCallback = { current, total, startTime ->
                updateProgressState(
                    current = current,
                    total = total,
                    startTime = startTime,
                    fileName = fileName,
                    currentFileIndex = currentFileIndex,
                    totalFiles = totalFiles
                )
            }
        )

        if (deleteOriginal) {
            _statusMessage.value = "Securely shredding encrypted archive..."
            SecureDelete.secureDelete(app, sourceFile)
        }
        
        return result
    }

    /**
     * Encrypt a raw java.io.File (device mode).
     */
    private suspend fun processDirectEncryption(
        file: File,
        password: CharArray,
        method: EncryptionMethod = EncryptionMethod.STANDARD,
        deleteOriginal: Boolean = false,
        keyfileBytes: ByteArray? = null,
        enableIntegrityCheck: Boolean = false,
        currentFileIndex: Int = 0,
        totalFiles: Int = 0
    ) {
        val app = getApplication<Application>()
        val mimeType = "application/octet-stream"
        val outName = "${file.name}.obfs"
        val fileSize = file.length()

        val outStream: java.io.OutputStream = when (val safUri = _currentOutputUri.value) {
            null -> {
                val sameDir = file.parentFile
                    ?: appDirectoryManager.getOutputDirectory()
                    ?: throw IllegalStateException("Could not determine output directory")
                java.io.FileOutputStream(uniqueFile(sameDir, outName))
            }
            else -> {
                val outputDir = DocumentFile.fromTreeUri(app, safUri)
                    ?: throw IllegalStateException("Output SAF directory not accessible")
                val targetFile = outputDir.createFile(mimeType, outName)
                    ?: throw IllegalStateException("Cannot create output file in SAF directory")
                app.contentResolver.openOutputStream(targetFile.uri)
                    ?: throw IllegalStateException("Cannot open output stream for SAF file")
            }
        }

        try {
            file.inputStream().use { inStream ->
                outStream.use { os ->
                    encryptionHelper.encrypt(
                        inputStream = inStream,
                        outputStream = os,
                        password = password,
                        method = method,
                        progressCallback = { current, total, startTime ->
                            updateProgressState(
                                current = current,
                                total = total,
                                startTime = startTime,
                                fileName = file.name,
                                currentFileIndex = currentFileIndex,
                                totalFiles = totalFiles
                            )
                        },
                        totalSize = fileSize,
                        keyfileBytes = keyfileBytes,
                        enableIntegrityCheck = enableIntegrityCheck,
                        isPaused = _isPaused.asStateFlow()
                    )
                }
            }
        } catch (e: Exception) {
            try { outStream.close() } catch (_: Exception) {}
            throw e
        }

        if (deleteOriginal) {
            _statusMessage.value = "Deleting original file..."
            if (!file.delete()) _statusMessage.value = "Warning: Could not delete original file"
        }
    }

    /**
     * Decrypt a raw java.io.File (device mode).
     */
    private suspend fun processDirectDecryption(
        file: File,
        password: CharArray,
        deleteOriginal: Boolean = false,
        keyfileBytes: ByteArray? = null,
        verifyIntegrity: Boolean = true,
        currentFileIndex: Int = 0,
        totalFiles: Int = 0
    ): DecryptionResult {
        val app = getApplication<Application>()
        val outName = file.name.removeSuffix(".obfs").ifEmpty { "Decrypted_${System.currentTimeMillis()}" }
        val fileSize = file.length()

        val outStream: java.io.OutputStream = when (val safUri = _currentOutputUri.value) {
            null -> {
                val sameDir = file.parentFile
                    ?: appDirectoryManager.getOutputDirectory()
                    ?: throw IllegalStateException("Could not determine output directory")
                java.io.FileOutputStream(uniqueFile(sameDir, outName))
            }
            else -> {
                val outputDir = DocumentFile.fromTreeUri(app, safUri)
                    ?: throw IllegalStateException("Output SAF directory not accessible")
                val targetFile = outputDir.createFile("*/*", outName)
                    ?: throw IllegalStateException("Cannot create output file in SAF directory")
                app.contentResolver.openOutputStream(targetFile.uri)
                    ?: throw IllegalStateException("Cannot open output stream for SAF file")
            }
        }

        val result = try {
            file.inputStream().use { inStream ->
                outStream.use { os ->
                    encryptionHelper.decrypt(
                        inputStream = inStream,
                        outputStream = os,
                        password = password,
                        method = EncryptionMethod.STANDARD,
                        progressCallback = { current, total, startTime ->
                            updateProgressState(
                                current = current,
                                total = total,
                                startTime = startTime,
                                fileName = file.name,
                                currentFileIndex = currentFileIndex,
                                totalFiles = totalFiles
                            )
                        },
                        totalSize = fileSize,
                        keyfileBytes = keyfileBytes,
                        verifyIntegrity = verifyIntegrity,
                        isPaused = _isPaused.asStateFlow()
                    )
                }
            }
        } catch (e: Exception) {
            try { outStream.close() } catch (_: Exception) {}
            throw e
        }

        if (deleteOriginal) {
            _statusMessage.value = "Deleting encrypted file..."
            if (!file.delete()) _statusMessage.value = "Warning: Could not delete encrypted file"
        }
        
        return result
    }

    // ─── Utilities ─────────────────────────────────────────────────────────────

    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val base = name.substringBeforeLast(".")
        val ext = if (name.contains(".")) ".${name.substringAfterLast(".")}" else ""
        var counter = 1
        while (f.exists()) {
            f = File(dir, "$base ($counter)$ext")
            counter++
        }
        return f
    }

    private fun collectJavaFiles(file: File, result: MutableList<File>) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { collectJavaFiles(it, result) }
        } else {
            result.add(file)
        }
    }

    private fun collectDocumentFiles(dir: DocumentFile, result: MutableList<DocumentFile>) {
        dir.listFiles().forEach { file ->
            if (file.isDirectory) collectDocumentFiles(file, result)
            else result.add(file)
        }
    }

    // ─── Helper methods for history tracking ───────────────────────────────────

    private fun getFileNameFromUri(uri: Uri): String {
        return try {
            val app = getApplication<Application>()
            DocumentFile.fromSingleUri(app, uri)?.name ?: uri.lastPathSegment ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun getFileSizeFromUri(uri: Uri): Long {
        return try {
            val app = getApplication<Application>()
            DocumentFile.fromSingleUri(app, uri)?.length() ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Clear all encryption history.
     */
    fun clearEncryptionHistory() {
        viewModelScope.launch {
            historyRepository.clearHistory()
        }
    }

    /**
     * Remove a specific item from history.
     */
    fun removeHistoryItem(itemId: String) {
        viewModelScope.launch {
            historyRepository.removeItem(itemId)
        }
    }

    fun cancelWork(id: java.util.UUID) {
        workManager.cancelWorkById(id)
    }
}
