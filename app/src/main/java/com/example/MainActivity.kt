package com.example

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.rememberAsyncImagePainter
import com.example.data.AppInfo
import com.example.data.SplitShortcut
import com.example.ui.SplitShortcutViewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.utils.ShortcutUtils

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
        val service = com.example.services.SplitScreenService.instance
        val pm = packageManager
        
        if (service != null) {
            // Robust automated flow: Launch Top -> Delay -> Split -> Delay -> Launch Bottom
            try {
                val topIntent = pm.getLaunchIntentForPackage(topPkg)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (topIntent != null) {
                    startActivity(topIntent)
                } else {
                    Toast.makeText(this, "Top app not found", Toast.LENGTH_SHORT).show()
                    return
                }

                // Wait for the first app to be fully visible
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    val splitSuccess = service.toggleSplitScreen()
                    
                    if (splitSuccess) {
                        // Wait for split-screen windowing to initialize
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            val bottomIntent = pm.getLaunchIntentForPackage(bottomPkg)?.apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
                            }
                            if (bottomIntent != null) {
                                try {
                                    startActivity(bottomIntent)
                                } catch (e: Exception) {
                                    Toast.makeText(this, "Launcher error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                            finish()
                        }, 800)
                    } else {
                        fallbackLaunch(topPkg, bottomPkg)
                    }
                }, 700)
            } catch (e: Exception) {
                Toast.makeText(this, "Auto-split process failed: ${e.message}", Toast.LENGTH_LONG).show()
                fallbackLaunch(topPkg, bottomPkg)
            }
        } else {
            fallbackLaunch(topPkg, bottomPkg)
        }
    }

    private fun fallbackLaunch(topPkg: String, bottomPkg: String) {
        try {
            val pm = packageManager
            val topIntent = pm.getLaunchIntentForPackage(topPkg)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val bottomIntent = pm.getLaunchIntentForPackage(bottomPkg)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
            }

            if (topIntent != null) {
                startActivity(topIntent)
                Toast.makeText(this, "Tip: Enable Accessibility for automatic split screens", Toast.LENGTH_LONG).show()
            }
            
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (bottomIntent != null) {
                    try {
                        startActivity(bottomIntent)
                    } catch (e: Exception) {
                        // ignore secondary error if not split
                    }
                }
                finish()
            }, 400)
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
    
    // Check if accessibility helper is active
    var isServiceActive by remember { 
        mutableStateOf(com.example.services.SplitScreenService.instance != null) 
    }

    // Refresh status when screen becomes visible or active
    LaunchedEffect(Unit) {
        while(true) {
            isServiceActive = com.example.services.SplitScreenService.instance != null
            kotlinx.coroutines.delay(1000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Split Screen Shortcuts", fontWeight = FontWeight.Bold) },
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
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isServiceActive) 
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else 
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isServiceActive) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isServiceActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isServiceActive) "Auto-Split Helper Active" else "Automatic Launcher Disabled",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isServiceActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isServiceActive) 
                                "Your shortcuts will launch and split screens automatically." 
                            else 
                                "Requires the offline Accessibility service to automate screen splitting.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!isServiceActive) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open settings", Toast.LENGTH_SHORT).show()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Enable", fontSize = 12.sp)
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
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
                            "Click the + icon below to pair two applications and pin them directly to your home screen.",
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
                items(shortcuts) { shortcut ->
                    ShortcutItem(
                        shortcut = shortcut,
                        onDelete = { viewModel.deleteShortcut(shortcut) },
                        onPin = { ShortcutUtils.createPinnedShortcut(context, shortcut) }
                    )
                }
            }
        }
    }
}
}

@Composable
fun ShortcutItem(
    shortcut: SplitShortcut,
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
                Text(
                    text = shortcut.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${shortcut.topPackage} / ${shortcut.bottomPackage}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Row {
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
    var topApp by remember { mutableStateOf<AppInfo?>(null) }
    var bottomApp by remember { mutableStateOf<AppInfo?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    
    val allApps by viewModel.installedApps.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val filteredApps = if (searchQuery.isBlank()) {
        allApps
    } else {
        allApps.filter { it.label.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true) }
    }

    var selectingFor by remember { mutableStateOf<String?>(null) } // "top" or "bottom"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Shortcut") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (topApp != null && bottomApp != null && name.isNotBlank()) {
                        IconButton(onClick = {
                            viewModel.saveShortcut(name, topApp!!.packageName, bottomApp!!.packageName) {
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

            Spacer(modifier = Modifier.height(24.dp))

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
                Spacer(modifier = Modifier.height(24.dp))
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
