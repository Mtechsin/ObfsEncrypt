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
import com.obfs.encrypt.data.SecureDelete
import com.obfs.encrypt.data.SettingsRepository
import com.obfs.encrypt.security.BiometricAuthManager
import com.obfs.encrypt.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val encryptionHelper: EncryptionHelper,
    private val settingsRepository: SettingsRepository,
    private val appDirectoryManager: AppDirectoryManager,
    val biometricAuthManager: BiometricAuthManager
) : AndroidViewModel(application) {

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

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
    
    // Keyfile support
    private val _keyfileUri = MutableStateFlow<Uri?>(null)
    val keyfileUri: StateFlow<Uri?> = _keyfileUri.asStateFlow()
    
    // Integrity check preference
    private val _enableIntegrityCheck = MutableStateFlow(false)
    val enableIntegrityCheck: StateFlow<Boolean> = _enableIntegrityCheck.asStateFlow()
    
    // Decryption result with integrity info
    private val _lastDecryptionResult = MutableStateFlow<DecryptionResult?>(null)
    val lastDecryptionResult: StateFlow<DecryptionResult?> = _lastDecryptionResult.asStateFlow()

    private var currentJob: Job? = null

    init {
        viewModelScope.launch {
            settingsRepository.secureDeleteOriginals.collect { enabled ->
                _secureDeleteOriginals.value = enabled
            }
        }
        viewModelScope.launch {
            settingsRepository.outputDirectoryUri.collect { uriString ->
                _currentOutputUri.value = uriString?.let { Uri.parse(it) }
            }
        }
        viewModelScope.launch {
            settingsRepository.themeMode.collect { mode ->
                _themeMode.value = mode
            }
        }
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
    
    fun setKeyfileUri(uri: Uri?) {
        _keyfileUri.value = uri
    }
    
    fun toggleIntegrityCheck(enabled: Boolean) {
        _enableIntegrityCheck.value = enabled
    }
    
    fun clearLastDecryptionResult() {
        _lastDecryptionResult.value = null
    }

    fun cancelOperation() {
        currentJob?.cancel()
        _isOperationActive.value = false
        _statusMessage.value = "Operation Cancelled"
        _progress.value = 0f
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
        currentJob = viewModelScope.launch(Dispatchers.IO) {
            _isOperationActive.value = true
            _progress.value = 0f
            try {
                // Read keyfile if set
                val keyfileBytes = _keyfileUri.value?.let { readKeyfile(it) }
                
                uris.forEachIndexed { index, uri ->
                    _statusMessage.value = "Encrypting file ${index + 1} of ${uris.size}..."
                    processSingleEncryption(uri, password, method, deleteOriginal, keyfileBytes, enableIntegrityCheck)
                }
                _statusMessage.value = "Encryption Complete!"
                _progress.value = 1f
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.localizedMessage}"
            } finally {
                password.fill('0')
                _isOperationActive.value = false
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
            try {
                // Read keyfile if set
                val keyfileBytes = _keyfileUri.value?.let { readKeyfile(it) }
                
                val rootFolder = DocumentFile.fromTreeUri(getApplication(), treeUri)
                if (rootFolder != null && rootFolder.isDirectory) {
                    val files = mutableListOf<DocumentFile>()
                    collectDocumentFiles(rootFolder, files)
                    files.forEachIndexed { index, file ->
                        _statusMessage.value = "Encrypting ${file.name} (${index + 1}/${files.size})"
                        processSingleEncryption(file.uri, password, method, deleteOriginal, keyfileBytes, enableIntegrityCheck)
                    }
                    _statusMessage.value = "Folder Encryption Complete!"
                    _progress.value = 1f
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.localizedMessage}"
            } finally {
                password.fill('0')
                _isOperationActive.value = false
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
        currentJob = viewModelScope.launch(Dispatchers.IO) {
            _isOperationActive.value = true
            _progress.value = 0f
            try {
                // Read keyfile if set
                val keyfileBytes = _keyfileUri.value?.let { readKeyfile(it) }
                
                uris.forEachIndexed { index, uri ->
                    _statusMessage.value = "Decrypting file ${index + 1} of ${uris.size}..."
                    val result = processSingleDecryption(uri, password, deleteOriginal, keyfileBytes, verifyIntegrity)
                    _lastDecryptionResult.value = result
                }
                _statusMessage.value = "Decryption Complete!"
                _progress.value = 1f
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.localizedMessage}"
            } finally {
                password.fill('0')
                _isOperationActive.value = false
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
            try {
                // Read keyfile if set
                val keyfileBytes = _keyfileUri.value?.let { readKeyfile(it) }
                
                val allFiles = mutableListOf<File>()
                for (f in files) collectJavaFiles(f, allFiles)

                if (allFiles.isEmpty()) {
                    _statusMessage.value = "No files to encrypt"
                    _isOperationActive.value = false
                    return@launch
                }

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
                    processDirectEncryption(file, password, method, deleteOriginal, keyfileBytes, enableIntegrityCheck)
                }
                _statusMessage.value = "Encryption Complete!"
                _progress.value = 1f
            } catch (e: Exception) {
                _statusMessage.value = "Error: ${e.localizedMessage}"
                e.printStackTrace()
            } finally {
                password.fill('0')
                _isOperationActive.value = false
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
            try {
                // Read keyfile if set
                val keyfileBytes = _keyfileUri.value?.let { readKeyfile(it) }
                
                val allFiles = mutableListOf<File>()
                for (f in files) collectJavaFiles(f, allFiles)

                if (allFiles.isEmpty()) {
                    _statusMessage.value = "No files to decrypt"
                    _isOperationActive.value = false
                    return@launch
                }

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
                    val result = processDirectDecryption(file, password, deleteOriginal, keyfileBytes, verifyIntegrity)
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
        enableIntegrityCheck: Boolean = false
    ) {
        val app = getApplication<Application>()
        val cr = app.contentResolver
        val sourceFile = DocumentFile.fromSingleUri(app, uri)
            ?: throw IllegalArgumentException("Could not read source file")

        val mimeType = "application/octet-stream"
        var outName = "${sourceFile.name ?: "File_${System.currentTimeMillis()}"}.obfs"
        val fileSize = sourceFile.length()

        val inStream = cr.openInputStream(sourceFile.uri)
            ?: throw IllegalStateException("Could not open source file for reading")

        val outStream: java.io.OutputStream = buildEncryptOutputStream(
            app = app,
            outName = outName,
            mimeType = mimeType,
            safParent = sourceFile.parentFile
        )

        encryptionHelper.encrypt(
            inputStream = inStream,
            outputStream = outStream,
            password = password,
            method = method,
            progressCallback = { current, total, _ ->
                _progress.value = (current.toFloat() / total.toFloat()).coerceIn(0.001f, 1f)
            },
            totalSize = fileSize,
            keyfileBytes = keyfileBytes,
            enableIntegrityCheck = enableIntegrityCheck
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
        verifyIntegrity: Boolean = true
    ): DecryptionResult {
        val app = getApplication<Application>()
        val cr = app.contentResolver
        val sourceFile = DocumentFile.fromSingleUri(app, uri) ?: throw IllegalArgumentException("Cannot read source file")

        val outName = sourceFile.name?.removeSuffix(".obfs")
            ?: "Decrypted_${System.currentTimeMillis()}"
        val fileSize = sourceFile.length()

        val inStream = cr.openInputStream(sourceFile.uri)
            ?: throw IllegalStateException("Could not open source file for reading")

        val outStream: java.io.OutputStream = buildDecryptOutputStream(
            app = app,
            outName = outName,
            safParent = sourceFile.parentFile
        )

        val result = encryptionHelper.decrypt(
            inputStream = inStream,
            outputStream = outStream,
            password = password,
            method = EncryptionMethod.STANDARD,
            progressCallback = { current, total, _ ->
                _progress.value = (current.toFloat() / total.toFloat()).coerceIn(0.001f, 1f)
            },
            totalSize = fileSize,
            keyfileBytes = keyfileBytes,
            verifyIntegrity = verifyIntegrity
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
        enableIntegrityCheck: Boolean = false
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
                        progressCallback = { current, total, _ ->
                            _progress.value = (current.toFloat() / total.toFloat()).coerceIn(0.001f, 1f)
                        },
                        totalSize = fileSize,
                        keyfileBytes = keyfileBytes,
                        enableIntegrityCheck = enableIntegrityCheck
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
        verifyIntegrity: Boolean = true
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
                        progressCallback = { current, total, _ ->
                            _progress.value = (current.toFloat() / total.toFloat()).coerceIn(0.001f, 1f)
                        },
                        totalSize = fileSize,
                        keyfileBytes = keyfileBytes,
                        verifyIntegrity = verifyIntegrity
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

    // ─── Output stream builders ────────────────────────────────────────────────

    private fun buildEncryptOutputStream(
        app: Application,
        outName: String,
        mimeType: String,
        safParent: DocumentFile?
    ): java.io.OutputStream {
        val safUri = _currentOutputUri.value

        if (safUri != null) {
            val dir = DocumentFile.fromTreeUri(app, safUri)
                ?: throw IllegalStateException("Output directory not found or not writable")
            val target = dir.createFile(mimeType, outName)
                ?: throw IllegalStateException("Cannot create output file in SAF directory")
            return app.contentResolver.openOutputStream(target.uri)
                ?: throw IllegalStateException("Could not open output stream")
        }

        if (safParent != null && safParent.canWrite()) {
            val target = safParent.createFile(mimeType, outName)
            if (target != null) {
                val stream = app.contentResolver.openOutputStream(target.uri)
                if (stream != null) return stream
                target.delete()
            }
        }

        val fallbackDir = appDirectoryManager.getOutputDirectory()
            ?: throw IllegalStateException("Could not access default output directory")
        return java.io.FileOutputStream(uniqueFile(fallbackDir, outName))
    }

    private fun buildDecryptOutputStream(
        app: Application,
        outName: String,
        safParent: DocumentFile?
    ): java.io.OutputStream {
        val safUri = _currentOutputUri.value

        if (safUri != null) {
            val dir = DocumentFile.fromTreeUri(app, safUri)
                ?: throw IllegalStateException("Output directory not found or not writable")
            val target = dir.createFile("*/*", outName)
                ?: throw IllegalStateException("Cannot create output file in SAF directory")
            return app.contentResolver.openOutputStream(target.uri)
                ?: throw IllegalStateException("Could not open output stream")
        }

        if (safParent != null && safParent.canWrite()) {
            val target = safParent.createFile("*/*", outName)
            if (target != null) {
                val stream = app.contentResolver.openOutputStream(target.uri)
                if (stream != null) return stream
                target.delete()
            }
        }

        val fallbackDir = appDirectoryManager.getOutputDirectory()
            ?: throw IllegalStateException("Could not access default output directory")
        return java.io.FileOutputStream(uniqueFile(fallbackDir, outName))
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
}
