package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GitHubRepository
import com.example.data.GitHubUser
import com.example.data.LocalProjectFile
import com.example.data.UploadStatus
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HistoryItem(
    val repoName: String,
    val htmlUrl: String,
    val isPrivate: Boolean,
    val uploadDate: String,
    val filesCount: Int
)

class GitHubUploaderViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GitHubRepository(application)
    private val prefs = application.getSharedPreferences("github_uploader_prefs", Context.MODE_PRIVATE)
    
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val historyListAdapterType = Types.newParameterizedType(List::class.java, HistoryItem::class.java)
    private val historyAdapter = moshi.adapter<List<HistoryItem>>(historyListAdapterType)

    // UI States
    private val _token = MutableStateFlow("")
    val token: StateFlow<String> = _token.asStateFlow()

    private val _username = MutableStateFlow("")
    val username: StateFlow<String> = _username.asStateFlow()

    private val _isLoggingIn = MutableStateFlow(false)
    val isLoggingIn: StateFlow<Boolean> = _isLoggingIn.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    private val _gitHubUser = MutableStateFlow<GitHubUser?>(null)
    val gitHubUser: StateFlow<GitHubUser?> = _gitHubUser.asStateFlow()

    private val _repoName = MutableStateFlow("")
    val repoName: StateFlow<String> = _repoName.asStateFlow()

    private val _repoDescription = MutableStateFlow("")
    val repoDescription: StateFlow<String> = _repoDescription.asStateFlow()

    private val _isPrivate = MutableStateFlow(true)
    val isPrivate: StateFlow<Boolean> = _isPrivate.asStateFlow()

    private val _selectedFiles = MutableStateFlow<List<LocalProjectFile>>(emptyList())
    val selectedFiles: StateFlow<List<LocalProjectFile>> = _selectedFiles.asStateFlow()

    private val _uploadStatus = MutableStateFlow<UploadStatus>(UploadStatus.Idle)
    val uploadStatus: StateFlow<UploadStatus> = _uploadStatus.asStateFlow()

    private val _uploadHistory = MutableStateFlow<List<HistoryItem>>(emptyList())
    val uploadHistory: StateFlow<List<HistoryItem>> = _uploadHistory.asStateFlow()

    init {
        // Load credentials and history
        _token.value = prefs.getString("saved_token", "") ?: ""
        _username.value = prefs.getString("saved_username", "") ?: ""
        loadHistory()
        
        // Auto validate if token exists
        if (_token.value.isNotEmpty()) {
            validateTokenAndFetchUser(_token.value)
        }
    }

    fun setToken(value: String) {
        _token.value = value
    }

    fun setUsername(value: String) {
        _username.value = value
    }

    fun setRepoName(value: String) {
        // Standardize immediately or in UI
        _repoName.value = value
    }

    fun setRepoDescription(value: String) {
        _repoDescription.value = value
    }

    fun setIsPrivate(value: Boolean) {
        _isPrivate.value = value
    }

    fun logout() {
        _token.value = ""
        _gitHubUser.value = null
        _loginError.value = null
        prefs.edit().remove("saved_token").remove("saved_username").apply()
    }

    fun validateTokenAndFetchUser(inputToken: String) {
        if (inputToken.isEmpty()) {
            _loginError.value = "الرجاء إدخال الرمز الخاص (Token)"
            return
        }

        viewModelScope.launch {
            _isLoggingIn.value = true
            _loginError.value = null
            try {
                val user = repository.getProfile(inputToken)
                _gitHubUser.value = user
                _token.value = inputToken
                _username.value = user.login
                
                // Save locally
                prefs.edit()
                    .putString("saved_token", inputToken)
                    .putString("saved_username", user.login)
                    .apply()
            } catch (e: Exception) {
                e.printStackTrace()
                _loginError.value = "فشل التحقق: تأكد من صحة الرمز ومن اتصال الإنترنت (${e.localizedMessage})"
                _gitHubUser.value = null
            } finally {
                _isLoggingIn.value = false
            }
        }
    }

    // Load folders or files
    fun loadProjectFromDirectory(uri: Uri) {
        viewModelScope.launch {
            try {
                val files = repository.traverseTree(uri)
                _selectedFiles.value = files
                
                // Auto guess a repository name from folder name if available
                val rootFile = androidx.documentfile.provider.DocumentFile.fromTreeUri(getApplication(), uri)
                rootFile?.name?.let { folderName ->
                    if (_repoName.value.isEmpty()) {
                        _repoName.value = sanitizeNameForRepo(folderName)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun loadIndividualFile(uri: Uri) {
        viewModelScope.launch {
            try {
                val file = repository.resolveSingleFile(uri)
                if (file != null) {
                    _selectedFiles.value = listOf(file)
                    
                    if (_repoName.value.isEmpty()) {
                        _repoName.value = sanitizeNameForRepo(file.path.substringBeforeLast("."))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun generateDemo() {
        val files = repository.generateDemoKotlinProject()
        _selectedFiles.value = files
        _repoName.value = "my-kotlin-app"
        _repoDescription.value = "تطبيق أندرويد تجريبي مكتوب بلغة Kotlin ومرفوع من هاتفي مباشرة."
    }

    fun clearSelectedFiles() {
        _selectedFiles.value = emptyList()
    }

    fun removeFileFromList(file: LocalProjectFile) {
        _selectedFiles.value = _selectedFiles.value.filter { it.path != file.path }
    }

    fun upload() {
        val currentToken = _token.value
        val name = _repoName.value
        val desc = _repoDescription.value
        val private = _isPrivate.value
        val files = _selectedFiles.value

        if (currentToken.isEmpty()) {
            _uploadStatus.value = UploadStatus.Error("الرجاء تسجيل الدخول أولاً قبل الرفع")
            return
        }
        if (name.isEmpty()) {
            _uploadStatus.value = UploadStatus.Error("الرجاء إدخال اسم المستودع (Repository Name)")
            return
        }
        if (files.isEmpty()) {
            _uploadStatus.value = UploadStatus.Error("لم يتم تحديد أي ملفات لرفعها!")
            return
        }

        viewModelScope.launch {
            repository.uploadProject(
                token = currentToken,
                repoName = name,
                description = desc,
                isPrivate = private,
                files = files
            ).collect { status ->
                _uploadStatus.value = status
                if (status is UploadStatus.Success) {
                    // Save to history
                    addToHistory(
                        HistoryItem(
                            repoName = status.repoName,
                            htmlUrl = status.repoUrl,
                            isPrivate = private,
                            uploadDate = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date()),
                            filesCount = files.size
                        )
                    )
                }
            }
        }
    }

    fun resetUploadStatus() {
        _uploadStatus.value = UploadStatus.Idle
    }

    private fun sanitizeNameForRepo(rawName: String): String {
        return rawName.lowercase()
            .replace(Regex("[^a-z0-9-_]"), "-")
            .replace(Regex("-+"), "-")
            .removePrefix("-")
            .removeSuffix("-")
    }

    // Save history
    private fun loadHistory() {
        try {
            val json = prefs.getString("upload_history", "[]") ?: "[]"
            val list = historyAdapter.fromJson(json) ?: emptyList()
            _uploadHistory.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun addToHistory(item: HistoryItem) {
        try {
            val updated = listOf(item) + _uploadHistory.value.filter { it.repoName != item.repoName }
            _uploadHistory.value = updated
            val json = historyAdapter.toJson(updated)
            prefs.edit().putString("upload_history", json).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clearHistory() {
        _uploadHistory.value = emptyList()
        prefs.edit().remove("upload_history").apply()
    }
}
