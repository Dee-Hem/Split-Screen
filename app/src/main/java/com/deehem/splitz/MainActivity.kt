package com.deehem.splitz

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.rememberAsyncImagePainter
import com.deehem.splitz.data.AppInfo
import com.deehem.splitz.data.SplitShortcut
import com.deehem.splitz.services.ServiceRecoveryHelper
import com.deehem.splitz.services.SplitScreenService
import com.deehem.splitz.ui.SplitShortcutViewModel
import com.deehem.splitz.ui.theme.MyApplicationTheme
import com.deehem.splitz.utils.ShortcutUtils

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Create recovery notification channel
        ServiceRecoveryHelper.createNotificationChannel(this)

        // Handle Split Launch logic
        handleIncomingIntent(intent)

        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()
                val viewModel: SplitShortcutViewModel = viewModel()

                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(navController, viewModel)
                    }
                    composable("create") {
                        CreateShortcutScreen(navController, viewModel)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intentToProcess: Intent?) {
        if (intentToProcess?.action == "LAUNCH_SPLIT") {
            val topPkg = intentToProcess.getStringExtra("topPackage")
            val bottomPkg = intentToProcess.getStringExtra("bottomPackage")
            if (topPkg != null && bottomPkg != null) {
                launchInSplitScreen(topPkg, bottomPkg)
            }
        }
    }

    private fun launchInSplitScreen(topPkg: String, bottomPkg: String) {
        val pm = packageManager
        val isSettingsEnabled = SplitScreenService.isSettingsEnabled(this)
        val isServiceLive = SplitScreenService.isServiceLive(this)

        if (isServiceLive || isSettingsEnabled) {
            // Wait briefly for live service instance if it is currently rebinding
            waitForServiceInstance { service ->
                if (service != null) {
                    try {
                        val topIntent = pm.getLaunchIntentForPackage(topPkg)?.apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                        }
                        if (topIntent != null) {
                            startActivity(topIntent)
                        } else {
                            Toast.makeText(this, "Top app not found", Toast.LENGTH_SHORT).show()
                            return@waitForServiceInstance
                        }

                        // Delay to allow top app to settle
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            service.toggleSplitScreen()

                            // Delay to allow split divider to establish, then start bottom app
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                val bottomIntent = pm.getLaunchIntentForPackage(bottomPkg)?.apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                                }
                                if (bottomIntent != null) {
                                    try {
                                        startActivity(bottomIntent)
                                    } catch (e: Exception) {
                                        val fallbackBottom = pm.getLaunchIntentForPackage(bottomPkg)?.apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        if (fallbackBottom != null) {
                                            startActivity(fallbackBottom)
                                        }
                                    }
                                }
                                finish()
                            }, 1000)
                        }, 1000)
                    } catch (e: Exception) {
                        Toast.makeText(this, "Auto-split process error: ${e.message}", Toast.LENGTH_LONG).show()
                        fallbackLaunch(topPkg, bottomPkg)
                    }
                } else {
                    // Settings says enabled, but service was killed/unbound by Android!
                    ServiceRecoveryHelper.notifyDisconnectIfNeeded(this)
                    Toast.makeText(
                        this,
                        "Splitz service was paused by Android system memory management. Please re-connect in Accessibility Settings.",
                        Toast.LENGTH_LONG
                    ).show()
                    fallbackLaunch(topPkg, bottomPkg)
                }
            }
        } else {
            fallbackLaunch(topPkg, bottomPkg)
        }
    }

    private fun waitForServiceInstance(
        attempts: Int = 12,
        delayMs: Long = 100,
        callback: (SplitScreenService?) -> Unit
    ) {
        val instance = SplitScreenService.instance
        if (instance != null) {
            callback(instance)
            return
        }
        if (attempts <= 0) {
            callback(null)
            return
        }
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            waitForServiceInstance(attempts - 1, delayMs, callback)
        }, delayMs)
    }

    private fun fallbackLaunch(topPkg: String, bottomPkg: String) {
        try {
            val pm = packageManager
            val topIntent = pm.getLaunchIntentForPackage(topPkg)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }
            val bottomIntent = pm.getLaunchIntentForPackage(bottomPkg)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }

            if (topIntent != null) {
                startActivity(topIntent)
                Toast.makeText(this, "Enable Splitz Accessibility for 1-click automatic split screens!", Toast.LENGTH_LONG).show()
            }

            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (bottomIntent != null) {
                    try {
                        startActivity(bottomIntent)
                    } catch (e: Exception) {
                        Toast.makeText(this, "Launch failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                finish()
            }, 800)
        } catch (e: Exception) {
            Toast.makeText(this, "Launch failed: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController, viewModel: SplitShortcutViewModel) {
    val shortcuts by viewModel.allShortcuts.collectAsState(initial = emptyList())
    val context = LocalContext.current

    // Check if accessibility helper is active and bound
    var isSettingsEnabled by remember { mutableStateOf(SplitScreenService.isSettingsEnabled(context)) }
    var isServiceLive by remember { mutableStateOf(SplitScreenService.isServiceLive(context)) }

    // Battery Optimization check
    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    var isIgnoringBattery by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
            } else true
        )
    }
    val prefs = remember { context.getSharedPreferences("splitz_ui_prefs", Context.MODE_PRIVATE) }
    var dismissedBatteryPrompt by remember { mutableStateOf(prefs.getBoolean("battery_prompt_dismissed", false)) }

    // Edit Shortcut Dialog State (Name + Folder)
    var editingShortcut by remember { mutableStateOf<SplitShortcut?>(null) }
    var editNameText by remember { mutableStateOf("") }
    var editFolderText by remember { mutableStateOf("") }

    // Folder Management State
    var renamingFolder by remember { mutableStateOf<String?>(null) }
    var renameFolderText by remember { mutableStateOf("") }
    var deletingFolder by remember { mutableStateOf<String?>(null) }

    // Collapsible folders tracking (default all expanded)
    val collapsedFolders = remember { mutableStateMapOf<String, Boolean>() }

    // Request Notification permission on Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Periodic state check
        while (true) {
            isSettingsEnabled = SplitScreenService.isSettingsEnabled(context)
            isServiceLive = SplitScreenService.isServiceLive(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                isIgnoringBattery = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
            }
            kotlinx.coroutines.delay(1000)
        }
    }

    // Extract unique existing folder names
    val existingFolders = remember(shortcuts) {
        shortcuts.mapNotNull { it.folder?.trim()?.ifEmpty { null } }.distinct().sorted()
    }

    // Group shortcuts by folder (null / blank -> "Uncategorized")
    val groupedShortcuts = remember(shortcuts) {
        val groups = shortcuts.groupBy { it.folder?.trim()?.ifEmpty { null } ?: "Uncategorized" }
        // Ensure standard ordering: named folders first, then Uncategorized
        val sortedKeys = groups.keys.sortedWith { a, b ->
            when {
                a == "Uncategorized" -> 1
                b == "Uncategorized" -> -1
                else -> a.compareTo(b, ignoreCase = true)
            }
        }
        sortedKeys.map { key -> key to (groups[key] ?: emptyList()) }
    }

    // Rename / Edit Shortcut Dialog
    if (editingShortcut != null) {
        AlertDialog(
            onDismissRequest = { editingShortcut = null },
            title = { Text("Edit Shortcut") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editNameText,
                        onValueChange = { editNameText = it },
                        label = { Text("Shortcut Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editFolderText,
                        onValueChange = { editFolderText = it },
                        label = { Text("Folder / Category (Optional)") },
                        placeholder = { Text("e.g. Work, Social, Uncategorized") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (existingFolders.isNotEmpty()) {
                        Text(
                            "Choose Existing Folder:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item {
                                FilterChip(
                                    selected = editFolderText.isBlank(),
                                    onClick = { editFolderText = "" },
                                    label = { Text("Uncategorized") }
                                )
                            }
                            items(existingFolders) { folder ->
                                FilterChip(
                                    selected = editFolderText.equals(folder, ignoreCase = true),
                                    onClick = { editFolderText = folder },
                                    label = { Text(folder) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editNameText.isNotBlank()) {
                            viewModel.updateShortcut(
                                shortcut = editingShortcut!!,
                                newName = editNameText.trim(),
                                newFolder = editFolderText.trim().ifEmpty { null }
                            )
                            editingShortcut = null
                        } else {
                            Toast.makeText(context, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingShortcut = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Rename Folder Dialog
    if (renamingFolder != null) {
        AlertDialog(
            onDismissRequest = { renamingFolder = null },
            title = { Text("Rename Folder") },
            text = {
                Column {
                    Text(
                        "Enter a new name for '${renamingFolder}':",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = renameFolderText,
                        onValueChange = { renameFolderText = it },
                        label = { Text("New Folder Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameFolderText.isNotBlank()) {
                            viewModel.renameFolder(renamingFolder!!, renameFolderText)
                            renamingFolder = null
                        } else {
                            Toast.makeText(context, "Folder name cannot be empty", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingFolder = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Folder Confirmation Dialog
    if (deletingFolder != null) {
        AlertDialog(
            onDismissRequest = { deletingFolder = null },
            title = { Text("Delete Folder") },
            text = {
                Text(
                    "Delete folder '${deletingFolder}'? All shortcuts inside will safely move to 'Uncategorized'.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        viewModel.deleteFolder(deletingFolder!!)
                        deletingFolder = null
                    }
                ) {
                    Text("Delete Folder")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingFolder = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Splitz", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("create") },
                modifier = Modifier.padding(bottom = 16.dp, end = 8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Shortcut")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Helper configuration state banner
            val serviceStatusColor = when {
                isServiceLive -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                isSettingsEnabled -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = serviceStatusColor)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when {
                            isServiceLive -> Icons.Default.CheckCircle
                            isSettingsEnabled -> Icons.Default.SyncProblem
                            else -> Icons.Default.Warning
                        },
                        contentDescription = null,
                        tint = when {
                            isServiceLive -> MaterialTheme.colorScheme.primary
                            isSettingsEnabled -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when {
                                isServiceLive -> "Splitz Automation Active"
                                isSettingsEnabled -> "Service Paused by Android"
                                else -> "Automation Service Disabled"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = when {
                                isServiceLive -> "1-click split screen shortcuts are fully active."
                                isSettingsEnabled -> "Enabled in Settings, but paused in background. Tap to reconnect."
                                else -> "Requires offline Accessibility permission to automate split screens."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!isServiceLive) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open settings", Toast.LENGTH_SHORT).show()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (isSettingsEnabled) "Reconnect" else "Enable", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Battery Optimization Banner (shows if not ignored and not dismissed)
            if (!isIgnoringBattery && !dismissedBatteryPrompt) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.BatteryChargingFull,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Prevent Background Kills",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Exempt Splitz from battery optimization to keep automation instant.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        TextButton(
                            onClick = {
                                dismissedBatteryPrompt = true
                                prefs.edit().putBoolean("battery_prompt_dismissed", true).apply()
                            }
                        ) {
                            Text("Later", fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                try {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                    }
                                } catch (e: Exception) {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                    } catch (ex: Exception) {
                                        Toast.makeText(context, "Could not open battery settings", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Exempt", fontSize = 12.sp)
                        }
                    }
                }
            }

            if (shortcuts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            Icons.Default.DashboardCustomize,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Create Your First Split-Screen Shortcut",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Tap the + icon below to pair two apps, assign a folder, and pin them directly to your home screen.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.8f),
                            modifier = Modifier.padding(horizontal = 16.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    groupedShortcuts.forEach { (folderName, folderShortcuts) ->
                        val isCollapsed = collapsedFolders[folderName] ?: false

                        // Folder Header
                        item(key = "header_$folderName") {
                            FolderHeader(
                                folderName = folderName,
                                count = folderShortcuts.size,
                                isCollapsed = isCollapsed,
                                onToggleCollapse = {
                                    collapsedFolders[folderName] = !isCollapsed
                                },
                                onRename = if (folderName != "Uncategorized") {
                                    {
                                        renamingFolder = folderName
                                        renameFolderText = folderName
                                    }
                                } else null,
                                onDelete = if (folderName != "Uncategorized") {
                                    { deletingFolder = folderName }
                                } else null
                            )
                        }

                        // Folder items when not collapsed
                        if (!isCollapsed) {
                            items(folderShortcuts, key = { it.id }) { shortcut ->
                                ShortcutItem(
                                    shortcut = shortcut,
                                    onEdit = {
                                        editingShortcut = shortcut
                                        editNameText = shortcut.name
                                        editFolderText = shortcut.folder ?: ""
                                    },
                                    onDelete = { viewModel.deleteShortcut(shortcut) },
                                    onPin = { ShortcutUtils.createPinnedShortcut(context, shortcut) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FolderHeader(
    folderName: String,
    count: Int,
    isCollapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onRename: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleCollapse)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (folderName == "Uncategorized") Icons.Default.FolderOpen else Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = folderName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))

            if (onRename != null) {
                IconButton(onClick = onRename, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Rename Folder",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Delete Folder",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            IconButton(onClick = onToggleCollapse, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = if (isCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = if (isCollapsed) "Expand" else "Collapse",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ShortcutItem(
    shortcut: SplitShortcut,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPin: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = shortcut.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    if (!shortcut.folder.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = shortcut.folder,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${shortcut.topPackage} / ${shortcut.bottomPackage}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Row {
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit Shortcut",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
                IconButton(onClick = onPin) {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = "Pin to Home",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateShortcutScreen(navController: NavController, viewModel: SplitShortcutViewModel) {
    var name by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf("") }
    var topApp by remember { mutableStateOf<AppInfo?>(null) }
    var bottomApp by remember { mutableStateOf<AppInfo?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val allApps by viewModel.installedApps.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val allShortcuts by viewModel.allShortcuts.collectAsState(initial = emptyList())

    val existingFolders = remember(allShortcuts) {
        allShortcuts.mapNotNull { it.folder?.trim()?.ifEmpty { null } }.distinct().sorted()
    }

    val filteredApps = if (searchQuery.isBlank()) {
        allApps
    } else {
        allApps.filter {
            it.label.contains(searchQuery, ignoreCase = true) ||
            it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    var selectingFor by remember { mutableStateOf<String?>(null) } // "top" or "bottom"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Shortcut") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (topApp != null && bottomApp != null && name.isNotBlank()) {
                        IconButton(onClick = {
                            viewModel.saveShortcut(
                                name = name.trim(),
                                topPkg = topApp!!.packageName,
                                bottomPkg = bottomApp!!.packageName,
                                folder = folder.trim().ifEmpty { null }
                            ) {
                                navController.popBackStack()
                            }
                        }) {
                            Icon(Icons.Default.Check, contentDescription = "Save")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Shortcut Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Folder assignment
            OutlinedTextField(
                value = folder,
                onValueChange = { folder = it },
                label = { Text("Folder / Category (Optional)") },
                placeholder = { Text("e.g. Work, Social, Uncategorized") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            if (existingFolders.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterChip(
                            selected = folder.isBlank(),
                            onClick = { folder = "" },
                            label = { Text("None") }
                        )
                    }
                    items(existingFolders) { existing ->
                        FilterChip(
                            selected = folder.equals(existing, ignoreCase = true),
                            onClick = { folder = existing },
                            label = { Text(existing) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectionCard(
                    modifier = Modifier.weight(1f),
                    label = "Top App",
                    app = topApp,
                    onClick = { selectingFor = "top" }
                )
                SelectionCard(
                    modifier = Modifier.weight(1f),
                    label = "Bottom App",
                    app = bottomApp,
                    onClick = { selectingFor = "bottom" }
                )
            }

            if (selectingFor != null) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Selecting ${if(selectingFor == "top") "Top" else "Bottom"} App",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search apps...") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(filteredApps) { app ->
                            AppListItem(app = app, onClick = {
                                if (selectingFor == "top") topApp = app else bottomApp = app
                                selectingFor = null
                                searchQuery = ""
                            })
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
                if (topApp != null && bottomApp != null && name.isBlank()) {
                    Text(
                        "Please enter a name for the shortcut",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }
}

@Composable
fun SelectionCard(modifier: Modifier, label: String, app: AppInfo?, onClick: () -> Unit) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (app != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
                    Image(
                        painter = rememberAsyncImagePainter(app.icon),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        app.label,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        fontSize = 10.sp
                    )
                }
            } else {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AppListItem(app: AppInfo, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(app.label) },
        supportingContent = { Text(app.packageName, fontSize = 12.sp) },
        leadingContent = {
            Image(
                painter = rememberAsyncImagePainter(app.icon),
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
