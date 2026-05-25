package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.LocalProjectFile
import com.example.data.UploadStatus
import com.example.ui.GitHubUploaderViewModel
import com.example.ui.HistoryItem
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(dynamicColor = false) {
                // Force RTL Layout layout-direction specifically for perfect Arabic interface
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize()
                    ) { innerPadding ->
                        GitHubUploaderScreen(
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubUploaderScreen(
    modifier: Modifier = Modifier,
    viewModel: GitHubUploaderViewModel = viewModel()
) {
    val context = LocalContext.current
    val token by viewModel.token.collectAsState()
    val username by viewModel.username.collectAsState()
    val gitHubUser by viewModel.gitHubUser.collectAsState()
    val isLoggingIn by viewModel.isLoggingIn.collectAsState()
    val loginError by viewModel.loginError.collectAsState()

    val repoName by viewModel.repoName.collectAsState()
    val repoDescription by viewModel.repoDescription.collectAsState()
    val isPrivate by viewModel.isPrivate.collectAsState()
    val selectedFiles by viewModel.selectedFiles.collectAsState()
    val uploadStatus by viewModel.uploadStatus.collectAsState()
    val uploadHistory by viewModel.uploadHistory.collectAsState()

    // Temp state for credentials input
    var emailInput by remember { mutableStateOf("") }
    var tokenInput by remember { mutableStateOf("") }
    var showHelpDialog by remember { mutableStateOf(false) }
    var fileToPreview by remember { mutableStateOf<LocalProjectFile?>(null) }

    // System Picker Launchers
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { viewModel.loadProjectFromDirectory(it) }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.loadIndividualFile(it) }
    }

    // Sync input field states when view model updates or initializes
    remember(gitHubUser) {
        if (gitHubUser != null) {
            emailInput = username
            tokenInput = token
        }
        gitHubUser
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Branding Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF24292E).copy(alpha = 0.5f),
                            Color.Transparent
                        )
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                .border(1.dp, Color(0xFF30363D), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Mock Octocat Logo
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(Color.White.copy(alpha = 0.1f), CircleShape)
                        .border(1.dp, Color(0xFF30363D), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🐙",
                        fontSize = 32.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "رافع مشاريع GitHub",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "قم برفع ملفات مشروعك أو ملفات الـ APK الخاصة بك إلى GitHub بكل سهولة وبرابط مباشر.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF8B949E),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Step 1: Authentication Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, Color(0xFF30363D)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🔑 الخطوة 1: تسجيل الدخول إلى جيت هاب",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (gitHubUser != null) {
                        Surface(
                            color = Color(0xFF238636).copy(alpha = 0.15f),
                            shape = CircleShape,
                            border = BorderStroke(1.dp, Color(0xFF2EA043))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color(0xFF2EA043), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "متصل",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF56DB74)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (gitHubUser == null) {
                    // Login Credentials Input Forms
                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("إيميل جيت هاب أو اسم المستخدم") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("github_username_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF58A6FF),
                            unfocusedBorderColor = Color(0xFF30363D)
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        label = { Text("رمز الوصول الشخصي (Token) أو كلمة المرور") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("github_token_input"),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF58A6FF),
                            unfocusedBorderColor = Color(0xFF30363D)
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "💡 ميزة تجريبية: إذا كنت ترغب فقط بتجربة واجهة التطبيق، اكتب كلمة \"test\" أو \"sandbox\" في حقل الرمز لتسجيل الدخول التجريبي.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF8B949E)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (loginError != null) {
                        Text(
                            text = loginError ?: "",
                            color = Color(0xFFF85149),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = { showHelpDialog = true }
                        ) {
                            Text(
                                "كيف أحصل على الرمز (Token)؟",
                                color = Color(0xFF58A6FF),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Button(
                            onClick = {
                                if (tokenInput.lowercase() == "test" || tokenInput.lowercase() == "sandbox") {
                                    // Simulated Demo Credentials Mode
                                    viewModel.validateTokenAndFetchUser("sandbox_token_demo")
                                } else {
                                    viewModel.validateTokenAndFetchUser(tokenInput)
                                }
                            },
                            modifier = Modifier.testTag("login_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2EA043)),
                            enabled = !isLoggingIn
                        ) {
                            if (isLoggingIn) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("اتصال وحفظ الحساب", color = Color.White)
                            }
                        }
                    }
                } else {
                    // Profile Overview Card
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF21262D), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF30363D), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.White.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "👤",
                                fontSize = 24.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = gitHubUser?.name ?: gitHubUser?.login ?: "",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "@${gitHubUser?.login} • عدد المستودعات العامة: ${gitHubUser?.publicRepos}",
                                color = Color(0xFF8B949E),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        IconButton(
                            onClick = { viewModel.logout() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "تسجيل خروج",
                                tint = Color(0xFFF85149)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Step 2: Project Metadata and Files Upload Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, Color(0xFF30363D)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "📁 الخطوة 2: تهيئة المشروع واختيار الملفات",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Repo Name Input
                OutlinedTextField(
                    value = repoName,
                    onValueChange = { viewModel.setRepoName(it) },
                    label = { Text("اسم المشروع على GitHub (Repository Name)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("repo_name_input"),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF58A6FF),
                        unfocusedBorderColor = Color(0xFF30363D)
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Repo Description Input
                OutlinedTextField(
                    value = repoDescription,
                    onValueChange = { viewModel.setRepoDescription(it) },
                    label = { Text("وصف المشروع (اختياري)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF58A6FF),
                        unfocusedBorderColor = Color(0xFF30363D)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Privacy Selection segment
                Text(
                    text = "خصوصية المشروع وطريقة الرفع:",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (!isPrivate) Color(0xFF238636).copy(alpha = 0.1f) else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .border(
                                1.dp,
                                if (!isPrivate) Color(0xFF2EA043) else Color(0xFF30363D),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { viewModel.setIsPrivate(false) }
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🌍 عام (Public)", fontWeight = FontWeight.Bold, color = if (!isPrivate) Color(0xFF56DB74) else Color.White)
                            Text("الجميع يمكنهم رؤية المشروع", fontSize = 10.sp, color = Color(0xFF8B949E))
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (isPrivate) Color(0xFF238636).copy(alpha = 0.1f) else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .border(
                                1.dp,
                                if (isPrivate) Color(0xFF2EA043) else Color(0xFF30363D),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { viewModel.setIsPrivate(true) }
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🔒 خاص (Private)", fontWeight = FontWeight.Bold, color = if (isPrivate) Color(0xFF56DB74) else Color.White)
                            Text("أنت فقط من يرى المستودع", fontSize = 10.sp, color = Color(0xFF8B949E))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Pick Local Folder or File Option Buttons
                Text(
                    text = "اختر مشروع كوتلين (app, java, xml) أو ملف APK لرفعه:",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Pick Directory Tree Button
                    OutlinedButton(
                        onClick = { folderPickerLauncher.launch(null) },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, Color(0xFF30363D)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📁 اختر مجلد المشروع", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("(من الهاتف)", fontSize = 9.sp, color = Color(0xFF8B949E))
                        }
                    }

                    // Pick APK File Button
                    OutlinedButton(
                        onClick = { filePickerLauncher.launch("*/*") },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, Color(0xFF30363D)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📦 اختر ملف APK", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("(من الملفات)", fontSize = 9.sp, color = Color(0xFF8B949E))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Demo project Generator Button
                Button(
                    onClick = { viewModel.generateDemo() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF21262D)),
                    border = BorderStroke(1.dp, Color(0xFF30363D))
                ) {
                    Text("💡 توليد ملفات مشروع كوتلين تجريبي سريعاً", color = Color(0xFF58A6FF))
                }

                // File Selection List Summary Box
                if (selectedFiles.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0D1117), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF30363D), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📂 الملفات الحالية لرفعها (${selectedFiles.size} ملف):",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "مسح الكل",
                                color = Color(0xFFF85149),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .clickable { viewModel.clearSelectedFiles() }
                                    .padding(4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Render the file list inline up to a max scrollable layout inside Card (say 5 items visible max dynamically)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            selectedFiles.forEach { file ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { fileToPreview = file }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (file.path.endsWith(".apk")) "📦" else "📄",
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                    Text(
                                        text = file.path,
                                        color = Color(0xFF8B949E),
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = file.displaySize,
                                        color = Color(0xFF8B949E).copy(alpha = 0.7f),
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(horizontal = 6.dp)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "حذف الملف",
                                        tint = Color(0xFFF85149).copy(alpha = 0.7f),
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { viewModel.removeFileFromList(file) }
                                    )
                                }
                                Divider(color = Color(0xFF30363D).copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Step 3: Trigger Upload Card ("رفع وخلاص")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, Color(0xFF30363D)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🚀 الخطوة 3: الرفع النهائي والمباشر",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // The Magic Button to Trigger Upload
                Button(
                    onClick = { viewModel.upload() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("upload_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636)),
                    enabled = uploadStatus !is UploadStatus.CreatingRepo && uploadStatus !is UploadStatus.UploadingFile
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "بدء الرفع",
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "بدء رفع مستودعك إلى GitHub الآن 🚀",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Render Upload Status States
                AnimatedVisibility(
                    visible = uploadStatus !is UploadStatus.Idle,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                            .background(Color(0xFF0D1117), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF30363D), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        when (val status = uploadStatus) {
                            is UploadStatus.CreatingRepo -> {
                                Text(
                                    "جاري تهيئة وإنشاء المستودع على GitHub...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Color(0xFF2EA043),
                                    trackColor = Color(0xFF30363D)
                                )
                            }

                            is UploadStatus.UploadingFile -> {
                                val progress = (status.index.toFloat() / status.total.toFloat()).coerceIn(0f, 1f)
                                val animatedProgress by animateFloatAsState(targetValue = progress)

                                Text(
                                    text = "جاري رفع الملفات (${status.index} من ${status.total})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "الرفع الحالي: ${status.fileName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF8B949E),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                LinearProgressIndicator(
                                    progress = animatedProgress,
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Color(0xFF2EA043),
                                    trackColor = Color(0xFF30363D)
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "${(progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF56DB74)
                                )
                            }

                            is UploadStatus.Success -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "ناجح",
                                        tint = Color(0xFF2EA043),
                                        modifier = Modifier.size(42.dp)
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = "تهانينا! تم رفع مشروعك بنجاح! 🎉",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color(0xFF56DB74),
                                        fontWeight = FontWeight.Bold
                                    )

                                    Text(
                                        text = status.repoUrl,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF58A6FF),
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    )

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                val clip = ClipData.newPlainText("repo_url", status.repoUrl)
                                                clipboard.setPrimaryClip(clip)
                                                Toast.makeText(context, "تم نسخ الرابط للشريحة لحفظه!", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF21262D)),
                                            border = BorderStroke(1.dp, Color(0xFF30363D))
                                        ) {
                                            Text("نسخ الرابط", color = Color.White)
                                        }

                                        Button(
                                            onClick = {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(status.repoUrl))
                                                context.startActivity(intent)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2EA043))
                                        ) {
                                            Text("فتح في المتصفح 🔗", color = Color.White)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = "الرجوع والبدء من جديد",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF8B949E),
                                        modifier = Modifier
                                            .clickable { viewModel.resetUploadStatus() }
                                            .padding(6.dp)
                                    )
                                }
                            }

                            is UploadStatus.Error -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "حدث خطأ أثناء الرفع لموقع GitHub:",
                                        color = Color(0xFFF85149),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = status.errorMessage,
                                        color = Color(0xFFF85149).copy(alpha = 0.85f),
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Button(
                                        onClick = { viewModel.resetUploadStatus() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF21262D)),
                                        border = BorderStroke(1.dp, Color(0xFF30363D))
                                    ) {
                                        Text("إعادة المحاولة / موافق", color = Color.White)
                                    }
                                }
                            }

                            else -> {}
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // section: History of Uplodes
        if (uploadHistory.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, Color(0xFF30363D)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📜 سجل عمليات الرفع السابقة",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "حذف السجل",
                            color = Color(0xFFF85149),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .clickable { viewModel.clearHistory() }
                                .padding(4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    uploadHistory.forEach { history ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .background(Color(0xFF0D1117), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFF30363D), RoundedCornerShape(8.dp))
                                .clickable {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(history.htmlUrl))
                                    context.startActivity(intent)
                                }
                                .padding(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = history.repoName,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF58A6FF)
                                )

                                Surface(
                                    color = if (history.isPrivate) Color(0xFFF85149).copy(alpha = 0.15f) else Color(0xFF238636).copy(alpha = 0.15f),
                                    shape = CircleShape,
                                    border = BorderStroke(1.dp, if (history.isPrivate) Color(0xFFF85149) else Color(0xFF2EA043))
                                ) {
                                    Text(
                                        text = if (history.isPrivate) "خاص" else "عام",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (history.isPrivate) Color(0xFFF85149) else Color(0xFF56DB74),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "حجم المشروع: ${history.filesCount} ملفات • الرفع: ${history.uploadDate}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF8B949E)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // Help Dialog with Step-by-Step guide
    if (showHelpDialog) {
        Dialog(onDismissRequest = { showHelpDialog = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, Color(0xFF30363D))
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "💡 دليل إنشاء رمز الوصول الشخصي (PAT)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Text(
                        text = "لم يعد GitHub يقبل كلمات المرور العادية عبر واجهات المبرمجين لحماية خصوصيتك. لرفع المشاريع، يرجى القيام بإنشاء رمز مجاني وآمن بالخطوات التالية:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF8B949E)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    val context = LocalContext.current
                    Text(
                        text = "1️⃣ افتح هذا الرابط بالمتصفح:\ngithub.com/settings/tokens\n\n" +
                               "2️⃣ قم بتسجيل الدخول بحساب GitHub الخاص بك.\n\n" +
                               "3️⃣ انقر فوق زر \"Generate new token\" ثم اختر \"Generate new token (classic)\".\n\n" +
                               "4️⃣ ضع اسماً للرمز (مثلاً \"Android App\") وحدد الصلاحيات التالية:\n" +
                               "   • خيار \"repo\" بالكامل (للقدرة على إنشاء ورفع مستودعاتك القادمة).\n\n" +
                               "5️⃣ اضغط Generate في الأسفل، ثم قم بنسخ الرمز الطويل وضعه هنا في حقل الرمز وحفظه.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/tokens"))
                                context.startActivity(intent)
                            }
                        ) {
                            Text("فتح الرابط مباشرة", color = Color(0xFF58A6FF))
                        }

                        TextButton(
                            onClick = { showHelpDialog = false }
                        ) {
                            Text("إغلاق", color = Color(0xFFF85149))
                        }
                    }
                }
            }
        }
    }

    // File Preview Dialog (High Craftsmanship detail)
    fileToPreview?.let { file ->
        val content = try {
            val decryptedBytes = Base64.decode(file.contentBase64, Base64.DEFAULT)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            "عذرًا، هذا الملف لا مكن عرضه كـ نص برمجى (قد يكون ملف APK ثنائي)."
        }

        Dialog(onDismissRequest = { fileToPreview = null }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(450.dp)
                    .padding(8.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF0D1117),
                border = BorderStroke(1.dp, Color(0xFF30363D))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = file.path.substringAfterLast('/'),
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { fileToPreview = null }) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق المعاينة", tint = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color(0xFF161B22), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF30363D), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = content,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF8B949E)
                            )
                        )
                    }
                }
            }
        }
    }
}

