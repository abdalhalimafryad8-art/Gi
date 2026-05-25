package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.documentfile.provider.DocumentFile
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.InputStream

data class LocalProjectFile(
    val path: String,
    val contentBase64: String,
    val displaySize: String
)

sealed class UploadStatus {
    object Idle : UploadStatus()
    object CreatingRepo : UploadStatus()
    data class UploadingFile(val index: Int, val total: Int, val fileName: String) : UploadStatus()
    data class Success(val repoName: String, val repoUrl: String) : UploadStatus()
    data class Error(val errorMessage: String) : UploadStatus()
}

class GitHubRepository(private val context: Context) {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val apiService: GitHubApiService = retrofit.create(GitHubApiService::class.java)

    suspend fun getProfile(token: String): GitHubUser {
        val formattedToken = formatToken(token)
        return apiService.getUser(formattedToken)
    }

    fun uploadProject(
        token: String,
        repoName: String,
        description: String?,
        isPrivate: Boolean,
        files: List<LocalProjectFile>
    ): Flow<UploadStatus> = flow {
        if (files.isEmpty()) {
            emit(UploadStatus.Error("الملفات المختارة فارغة! فضلاً اختر files أو أنشئ مشروعاً تجريبياً أولاً."))
            return@flow
        }

        val formattedToken = formatToken(token)

        // 1. Create Repository
        emit(UploadStatus.CreatingRepo)
        val repoResponse = try {
            apiService.createRepository(
                token = formattedToken,
                request = CreateRepoRequest(
                    name = sanitizeRepoName(repoName),
                    description = description ?: "مرفوع بواسطة تطبيق رافع مشاريع GitHub لـ Android",
                    private = isPrivate,
                    autoInit = false
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            // Try to extract body error message if serializable or show general message
            val errMsg = e.localizedMessage ?: "فشل إنشاء المستودع (تأكد من اختيار اسم فريد وغير مكرر)"
            emit(UploadStatus.Error("حدث خطأ في إنشاء المستودع على جيت هاب: $errMsg"))
            return@flow
        }

        val owner = repoResponse.owner.login
        val actualRepoName = repoResponse.name

        // 2. Upload Each File
        files.forEachIndexed { index, file ->
            emit(UploadStatus.UploadingFile(
                index = index + 1,
                total = files.size,
                fileName = file.path
            ))

            try {
                // Slashes in path should be URL-encoded? 
                // Wait! Retrofit treats @Path(encoded = true) as "already encoded". 
                // So if we pass a raw path like "app/src/main/java/MainActivity.kt", 
                // the slash chars are NOT escaped, which makes it hit user/repo/contents/app/src/main/....
                // This is EXACTLY what GitHub API content creation expects!
                apiService.uploadFile(
                    token = formattedToken,
                    owner = owner,
                    repo = actualRepoName,
                    path = file.path,
                    request = UploadFileRequest(
                        message = "إضافة ملف ${file.path.substringAfterLast('/')} عبر تطبيق الأندرويد",
                        content = file.contentBase64
                    )
                )
                // Add a small delay between requests to be safe from GitHub rate limiting / abuse limits
                delay(300)
            } catch (e: Exception) {
                e.printStackTrace()
                // If a single file upload fails, we keep going or report? 
                // Let's print the error but continue to be robust, or we can abort on blocker
                // If it fails, let's wait a bit and retry once
                try {
                    delay(500)
                    apiService.uploadFile(
                        token = formattedToken,
                        owner = owner,
                        repo = actualRepoName,
                        path = file.path,
                        request = UploadFileRequest(
                            message = "إعادة محاولة: إضافة ملف ${file.path.substringAfterLast('/')}",
                            content = file.contentBase64
                        )
                    )
                } catch (retryEx: Exception) {
                    retryEx.printStackTrace()
                    emit(UploadStatus.Error("فشلت عملية رفع الملف ${file.path}: ${retryEx.localizedMessage}"))
                    return@flow
                }
            }
        }

        emit(UploadStatus.Success(
            repoName = actualRepoName,
            repoUrl = repoResponse.htmlUrl
        ))
    }.flowOn(Dispatchers.IO)

    // Helpers
    private fun formatToken(token: String): String {
        return if (token.trim().startsWith("Bearer ") || token.trim().startsWith("token ")) {
            token.trim()
        } else {
            "token ${token.trim()}"
        }
    }

    private fun sanitizeRepoName(name: String): String {
        return name.trim()
            .replace(Regex("[^a-zA-Z0-9-_]"), "-") // replace non-safe chars with hyphen
            .replace(Regex("-+"), "-")             // collapse multiple hyphens
            .removePrefix("-")
            .removeSuffix("-")
            .ifEmpty { "my-github-project" }
    }

    // Traverse Document Tree
    fun traverseTree(treeUri: Uri): List<LocalProjectFile> {
        val rootFile = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val files = mutableListOf<LocalProjectFile>()

        fun scan(file: DocumentFile, relativePath: String) {
            val name = file.name ?: return
            if (file.isDirectory) {
                val newPath = if (relativePath.isEmpty()) name else "$relativePath/$name"
                // Skip common large folders or hidden lists to optimize upload and avoid memory bloat
                if (name == ".git" || name == ".gradle" || name == "build" || name == ".idea" || name == "node_modules") {
                    return
                }
                file.listFiles().forEach { child ->
                    scan(child, newPath)
                }
            } else if (file.isFile) {
                val fullRelPath = if (relativePath.isEmpty()) name else "$relativePath/$name"
                
                // Skip build files or key files
                if (name.startsWith(".") || name == "local.properties" || name.endsWith(".jks") || name.endsWith(".png") && fullRelPath.contains("build/")) {
                    return
                }

                try {
                    context.contentResolver.openInputStream(file.uri)?.use { inputStream ->
                        val bytes = inputStream.readBytes()
                        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        val sizeStr = formatFileSize(bytes.size.toLong())
                        files.add(LocalProjectFile(path = fullRelPath, contentBase64 = base64, displaySize = sizeStr))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        rootFile.listFiles().forEach { child ->
            scan(child, "")
        }
        return files
    }

    // Resolve Single File (e.g., APK)
    fun resolveSingleFile(uri: Uri): LocalProjectFile? {
        val documentFile = DocumentFile.fromSingleUri(context, uri)
        val name = documentFile?.name ?: "app-release.apk"
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val bytes = inputStream.readBytes()
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val sizeStr = formatFileSize(bytes.size.toLong())
                return LocalProjectFile(path = name, contentBase64 = base64, displaySize = sizeStr)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, 2)
        return String.format(java.util.Locale.US, "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    // Generate Beautiful Demo Kotlin Project
    fun generateDemoKotlinProject(): List<LocalProjectFile> {
        val list = mutableListOf<LocalProjectFile>()

        fun add(path: String, content: String) {
            val bytes = content.toByteArray()
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val sizeStr = formatFileSize(bytes.size.toLong())
            list.add(LocalProjectFile(path, b64, sizeStr))
        }

        // 1. Settings Gradle
        add("settings.gradle.kts", """
            pluginManagement {
                repositories {
                    google()
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                repositories {
                    google()
                    mavenCentral()
                }
            }
            rootProject.name = "MyAwesomeKotlinApp"
            include(":app")
        """.trimIndent())

        // 2. Project Build Gradle
        add("build.gradle.kts", """
            plugins {
                alias(libs.plugins.android.application) apply false
                alias(libs.plugins.kotlin.android) apply false
            }
        """.trimIndent())

        // 3. App Build Gradle
        add("app/build.gradle.kts", """
            plugins {
                alias(libs.plugins.android.application)
                alias(libs.plugins.kotlin.android)
            }

            android {
                namespace = "com.example.awesomeapp"
                compileSdk = 34

                defaultConfig {
                    applicationId = "com.example.awesomeapp"
                    minSdk = 24
                    targetSdk = 34
                    versionCode = 1
                    versionName = "1.0"
                }

                buildTypes {
                    release {
                        isMinifyEnabled = false
                    }
                }
            }

            dependencies {
                implementation("androidx.core:core-ktx:1.12.0")
                implementation("androidx.appcompat:appcompat:1.6.1")
                implementation("com.google.android.material:material:1.11.0")
            }
        """.trimIndent())

        // 4. Android Manifest
        add("app/src/main/AndroidManifest.xml", """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application
                    android:allowBackup="true"
                    android:icon="@mipmap/ic_launcher"
                    android:label="@string/app_name"
                    android:theme="@style/Theme.AppCompat.Light.DarkActionBar">
                    <activity
                        android:name=".MainActivity"
                        android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                </application>
            </manifest>
        """.trimIndent())

        // 5. Strings Resource
        add("app/src/main/res/values/strings.xml", """
            <resources>
                <string name="app_name">My Awesome App</string>
                <string name="welcome_message">مرحباً بك في تطبيقي الأول بلغة كوتلين!</string>
            </resources>
        """.trimIndent())

        // 6. Layout XML
        add("app/src/main/res/layout/activity_main.xml", """
            <?xml version="1.0" encoding="utf-8"?>
            <RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_size"
                android:layout_height="match_size"
                android:padding="16dp">

                <TextView
                    android:id="@+id/textView"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_centerInParent="true"
                    android:text="@string/welcome_message"
                    android:textSize="20sp"
                    android:textStyle="bold" />

                <Button
                    android:id="@+id/button"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_below="@id/textView"
                    android:layout_centerHorizontal="true"
                    android:layout_marginTop="20dp"
                    android:text="اضغط هنا" />

            </RelativeLayout>
        """.trimIndent())

        // 7. MainActivity Kotlin
        add("app/src/main/java/com/example/awesomeapp/MainActivity.kt", """
            package com.example.awesomeapp

            import android.os.Bundle
            import android.widget.Button
            import android.widget.Toast
            import androidx.appcompat.app.AppCompatActivity

            class MainActivity : AppCompatActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setContentView(R.layout.activity_main)

                    val btn = findViewById<Button>(R.id.button)
                    btn.setOnClickListener {
                        Toast.makeText(this, "شكراً لتجربة التطبيق الرائع!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        """.trimIndent())

        return list
    }
}
