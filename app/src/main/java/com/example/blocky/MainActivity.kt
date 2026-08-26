package com.example.blocky

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.blocky.data.*
import com.example.blocky.ui.theme.BlockyTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val settings = SettingsManager(newBase)
        val locale = Locale.forLanguageTag(settings.languageCode)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BlockyTheme {
                MainContainer()
            }
        }
    }
}

@Composable
fun MainContainer() {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val db = remember { AppDatabase.getDatabase(context) }
    val blockLogDao = remember { db.blockedCallDao() }
    val permanentBlockDao = remember { db.permanentBlockedNumberDao() }
    val whitelistDao = remember { db.whitelistedNumberDao() }
    val unblockedDao = remember { db.unblockedNumberDao() }
    val scope = rememberCoroutineScope()

    var isOnboardingCompleted by remember { mutableStateOf(settingsManager.isOnboardingCompleted) }
    var roleHeldState by remember { mutableStateOf(checkRoleHeld(context)) }
    var isEnabled by remember { mutableStateOf(settingsManager.isBlockingEnabled) }
    var currentLang by remember { mutableStateOf(settingsManager.languageCode) }
    
    val isProtectionActive = roleHeldState && isEnabled

    LaunchedEffect(isProtectionActive) {
        val intent = Intent(context, StatusIndicatorService::class.java)
        if (isProtectionActive) {
            val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                checkPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            } else {
                true
            }
            if (hasNotificationPermission) {
                try {
                    context.startForegroundService(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            try {
                context.stopService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val startOfDay = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val blockedCount by blockLogDao.getDailyBlockedCount(startOfDay).collectAsState(initial = 0)
    val historyLog by blockLogDao.getBlockedCallsSince(startOfDay).collectAsState(initial = emptyList())
    val permanentBlockedList by permanentBlockDao.getAll().collectAsState(initial = emptyList())
    val whitelist by whitelistDao.getAllWhitelisted().collectAsState(initial = emptyList())

    Crossfade(targetState = isOnboardingCompleted, label = "ScreenTransition") { completed ->
        if (completed) {
            MainContent(
                roleHeld = roleHeldState,
                isEnabled = isEnabled,
                blockedCount = blockedCount,
                historyLog = historyLog,
                permanentBlockedList = permanentBlockedList,
                whitelist = whitelist,
                currentLang = currentLang,
                onRoleChanged = { roleHeldState = it },
                onEnabledChanged = {
                    isEnabled = it
                    settingsManager.isBlockingEnabled = it
                },
                onLanguageChanged = { lang ->
                    settingsManager.languageCode = lang
                    currentLang = lang
                    (context as? ComponentActivity)?.recreate()
                },
                onClearHistory = {
                    scope.launch { blockLogDao.clearAll() }
                },
                onUnblockNumber = { numberObj ->
                    scope.launch { 
                        permanentBlockDao.delete(numberObj)
                        unblockedDao.insert(UnblockedNumber(phoneNumber = numberObj.phoneNumber))
                    }
                },
                onUnblockAll = {
                    scope.launch {
                        permanentBlockedList.forEach {
                            unblockedDao.insert(UnblockedNumber(phoneNumber = it.phoneNumber))
                        }
                        permanentBlockDao.clearAll()
                        Toast.makeText(context, R.string.toast_unblocked_all, Toast.LENGTH_SHORT).show()
                    }
                },
                onAddToWhitelistFromBlocked = { numberObj ->
                    scope.launch {
                        permanentBlockDao.delete(numberObj)
                        unblockedDao.insert(UnblockedNumber(phoneNumber = numberObj.phoneNumber))
                        whitelistDao.insert(WhitelistedNumber(phoneNumber = numberObj.phoneNumber))
                        Toast.makeText(context, R.string.toast_whitelisted, Toast.LENGTH_SHORT).show()
                    }
                },
                onAddToWhitelistManual = { number ->
                    scope.launch {
                        unblockedDao.deleteByNumber(number)
                        whitelistDao.insert(WhitelistedNumber(phoneNumber = number))
                        Toast.makeText(context, R.string.toast_whitelisted, Toast.LENGTH_SHORT).show()
                    }
                },
                onRemoveFromWhitelist = { number ->
                    scope.launch { whitelistDao.delete(number) }
                }
            ) {
                isOnboardingCompleted = false
            }
        } else {
            OnboardingScreen(
                currentLang = currentLang,
                onLanguageChanged = { lang ->
                    settingsManager.languageCode = lang
                    currentLang = lang
                    (context as? ComponentActivity)?.recreate()
                },
                onCompleted = {
                    scope.launch {
                        settingsManager.isOnboardingCompleted = true
                        isOnboardingCompleted = true
                        roleHeldState = checkRoleHeld(context)
                    }
                }
            )
        }
    }
}

@Composable
fun OnboardingScreen(
    currentLang: String,
    onLanguageChanged: (String) -> Unit,
    onCompleted: () -> Unit
) {
    val context = LocalContext.current
    val isInPreview = LocalInspectionMode.current
    
    var notificationState by remember {
        mutableStateOf(
            if (isInPreview) {
                true
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                checkPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            } else {
                true
            }
        )
    }
    var contactsState by remember { mutableStateOf(if (isInPreview) true else checkPermission(context, Manifest.permission.READ_CONTACTS)) }
    var batteryState by remember { mutableStateOf(if (isInPreview) true else isIgnoringBatteryOptimizations(context)) }
    var roleState by remember { mutableStateOf(if (isInPreview) true else checkRoleHeld(context)) }

    // Prominent Disclosure state dialogs
    var showContactsDisclosure by remember { mutableStateOf(false) }
    var showNotificationDisclosure by remember { mutableStateOf(false) }
    var showPrivacyPolicyModal by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationState = checkPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        }
        contactsState = checkPermission(context, Manifest.permission.READ_CONTACTS)
    }

    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
        roleState = checkRoleHeld(context)
    }

    if (showContactsDisclosure) {
        AlertDialog(
            onDismissRequest = { showContactsDisclosure = false },
            icon = { Icon(Icons.Default.Contacts, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.disclosure_contacts_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.disclosure_contacts_desc)) },
            confirmButton = {
                Button(onClick = {
                    showContactsDisclosure = false
                    permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                }) {
                    Text(stringResource(R.string.disclosure_accept_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = { showContactsDisclosure = false }) {
                    Text(stringResource(R.string.disclosure_decline_btn))
                }
            }
        )
    }

    if (showNotificationDisclosure) {
        AlertDialog(
            onDismissRequest = { showNotificationDisclosure = false },
            icon = { Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.disclosure_notifications_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.disclosure_notifications_desc)) },
            confirmButton = {
                Button(onClick = {
                    showNotificationDisclosure = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }) {
                    Text(stringResource(R.string.disclosure_accept_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationDisclosure = false }) {
                    Text(stringResource(R.string.disclosure_decline_btn))
                }
            }
        )
    }

    if (showPrivacyPolicyModal) {
        PrivacyPolicyDialog(onDismiss = { showPrivacyPolicyModal = false })
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 540.dp)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = currentLang == "en", onClick = { onLanguageChanged("en") }, label = { Text(stringResource(R.string.lang_english)) })
                    FilterChip(selected = currentLang == "es", onClick = { onLanguageChanged("es") }, label = { Text(stringResource(R.string.lang_spanish)) })
                }
                IconButton(onClick = { showPrivacyPolicyModal = true }) {
                    Icon(Icons.Default.PrivacyTip, contentDescription = stringResource(R.string.privacy_policy_title), tint = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Icon(imageVector = Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Text(text = stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(text = stringResource(R.string.welcome_desc), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            
            Spacer(modifier = Modifier.height(10.dp))

            OnboardingStep(
                title = stringResource(R.string.step_role_title),
                desc = stringResource(R.string.step_role_desc),
                isDone = roleState,
                btnText = stringResource(R.string.set_default_app),
            ) {
                val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                roleManager?.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)?.let { roleLauncher.launch(it) }
            }

            OnboardingStep(
                title = stringResource(R.string.step_contacts_title),
                desc = stringResource(R.string.step_contacts_desc),
                isDone = contactsState,
                btnText = stringResource(R.string.grant_permission),
            ) { 
                showContactsDisclosure = true
            }

            OnboardingStep(
                title = stringResource(R.string.step_notification_title),
                desc = stringResource(R.string.step_notification_desc),
                isDone = notificationState,
                btnText = stringResource(R.string.grant_permission),
            ) { 
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    showNotificationDisclosure = true
                }
            }

            OnboardingStep(
                title = stringResource(R.string.step_battery_title),
                desc = stringResource(R.string.step_battery_desc),
                isDone = batteryState,
                btnText = stringResource(R.string.grant_permission),
            ) {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            
            Button(
                onClick = onCompleted,
                modifier = Modifier.fillMaxWidth(),
                enabled = roleState
            ) {
                Text(text = if (roleState && contactsState) stringResource(R.string.setup_complete) else stringResource(R.string.get_started))
            }

            TextButton(onClick = { showPrivacyPolicyModal = true }) {
                Text(stringResource(R.string.privacy_policy_btn), fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun OnboardingStep(title: String, desc: String, isDone: Boolean, btnText: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(text = desc, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (isDone) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
            } else {
                TextButton(onClick = onClick) { Text(btnText, fontSize = 12.sp) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(
    roleHeld: Boolean,
    isEnabled: Boolean,
    blockedCount: Int,
    historyLog: List<BlockedCall>,
    permanentBlockedList: List<PermanentBlockedNumber>,
    whitelist: List<WhitelistedNumber>,
    currentLang: String,
    onRoleChanged: (Boolean) -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
    onLanguageChanged: (String) -> Unit,
    onClearHistory: () -> Unit,
    onUnblockNumber: (PermanentBlockedNumber) -> Unit,
    onUnblockAll: () -> Unit,
    onAddToWhitelistFromBlocked: (PermanentBlockedNumber) -> Unit,
    onAddToWhitelistManual: (String) -> Unit,
    onRemoveFromWhitelist: (WhitelistedNumber) -> Unit,
    onFinishOnboarding: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val isInPreview = LocalInspectionMode.current
    var showHistory by remember { mutableStateOf(false) }
    var showPrivacyPolicyModal by remember { mutableStateOf(false) }
    
    val isSetupIncomplete = !roleHeld || (!isInPreview && !checkPermission(context, Manifest.permission.READ_CONTACTS))

    if (showPrivacyPolicyModal) {
        PrivacyPolicyDialog(onDismiss = { showPrivacyPolicyModal = false })
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                navigationIcon = {
                    if (selectedTab == 0) {
                        IconButton(onClick = { showHistory = true }) {
                            Icon(Icons.Default.History, contentDescription = stringResource(R.string.content_description_history))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showPrivacyPolicyModal = true }) {
                        Icon(Icons.Default.PrivacyTip, contentDescription = stringResource(R.string.privacy_policy_title))
                    }
                    val isProtectionActive = roleHeld && isEnabled
                    if (isProtectionActive) {
                        Icon(imageVector = Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.padding(end = 16.dp))
                    } else {
                        Icon(imageVector = Icons.Default.GppBad, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.padding(end = 16.dp))
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(icon = { Icon(Icons.Default.Shield, null) }, label = { Text(stringResource(R.string.protection_tab)) }, selected = selectedTab == 0, onClick = { selectedTab = 0 })
                NavigationBarItem(icon = { Icon(Icons.Default.Block, null) }, label = { Text(stringResource(R.string.blocked_list_tab)) }, selected = selectedTab == 1, onClick = { selectedTab = 1 })
                NavigationBarItem(icon = { Icon(Icons.AutoMirrored.Filled.FormatListBulleted, null) }, label = { Text(stringResource(R.string.whitelist_tab)) }, selected = selectedTab == 2, onClick = { selectedTab = 2 })
                NavigationBarItem(icon = { Icon(Icons.AutoMirrored.Filled.Help, null) }, label = { Text(stringResource(R.string.support_tab)) }, selected = selectedTab == 3, onClick = { selectedTab = 3 })
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (isSetupIncomplete && (selectedTab == 0)) {
                        Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(text = stringResource(R.string.missing_setup_warning), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                                TextButton(onClick = onFinishOnboarding) { Text(stringResource(R.string.finish_setup_btn)) }
                            }
                        }
                    }
                    
                    Box(modifier = Modifier.weight(1f)) {
                        when (selectedTab) {
                            0 -> BlockyScreen(
                                blockedCount = blockedCount,
                                isRoleHeldInitial = roleHeld,
                                onRoleChanged = onRoleChanged,
                                isEnabledInitial = isEnabled,
                                onEnabledChanged = onEnabledChanged,
                                currentLang = currentLang,
                                onLanguageChanged = onLanguageChanged,
                            )
                            1 -> BlockedListScreen(
                                blockedList = permanentBlockedList,
                                onUnblock = onUnblockNumber,
                                onUnblockAll = onUnblockAll,
                                onWhitelist = onAddToWhitelistFromBlocked
                            )
                            2 -> WhitelistScreen(
                                whitelist = whitelist,
                                onRemove = onRemoveFromWhitelist,
                                onAddManual = onAddToWhitelistManual
                            )
                            3 -> TroubleshootingScreen(onShowPrivacyPolicy = { showPrivacyPolicyModal = true })
                        }
                    }
                }
            }
        }

        if (showHistory) {
            ModalBottomSheet(onDismissRequest = { showHistory = false }) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth()) {
                        HistoryContent(history = historyLog, onClear = onClearHistory)
                    }
                }
            }
        }
    }
}

@Composable
fun BlockyScreen(
    modifier: Modifier = Modifier,
    isRoleHeldInitial: Boolean,
    onRoleChanged: (Boolean) -> Unit,
    isEnabledInitial: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    currentLang: String,
    onLanguageChanged: (String) -> Unit,
    blockedCount: Int = 0,
) {
    val context = LocalContext.current
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
        onRoleChanged(checkRoleHeld(context))
    }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = stringResource(R.string.app_name), fontSize = 48.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = if (isRoleHeldInitial) stringResource(R.string.protection_active) else stringResource(R.string.protection_inactive), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = blockedCount.toString(), fontSize = 64.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.secondary)
        Text(text = stringResource(R.string.calls_blocked_label), style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(48.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (isRoleHeldInitial && isEnabledInitial) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer)) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = if (isRoleHeldInitial && isEnabledInitial) stringResource(R.string.shield_active) else stringResource(R.string.shield_down), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Text(text = when { !isRoleHeldInitial -> stringResource(R.string.shield_down_desc) !isEnabledInitial -> stringResource(R.string.service_disabled) else -> stringResource(R.string.shield_active_desc) }, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (isRoleHeldInitial) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = if (isEnabledInitial) stringResource(R.string.service_enabled) else stringResource(R.string.service_disabled), style = MaterialTheme.typography.titleMedium)
                Switch(checked = isEnabledInitial, onCheckedChange = onEnabledChanged)
            }
        } else {
            Button(onClick = {
                val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                roleManager?.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)?.let { roleLauncher.launch(it) }
            }, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.enable_protection_btn))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text(text = stringResource(R.string.language_selection), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            FilterChip(selected = currentLang == "en", onClick = { onLanguageChanged("en") }, label = { Text(stringResource(R.string.lang_english)) })
            FilterChip(selected = currentLang == "es", onClick = { onLanguageChanged("es") }, label = { Text(stringResource(R.string.lang_spanish)) })
        }
    }
}

@Composable
fun HistoryContent(history: List<BlockedCall>, onClear: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = stringResource(R.string.block_history_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            if (history.isNotEmpty()) {
                IconButton(onClick = onClear) { Icon(Icons.Default.DeleteSweep, null, tint = MaterialTheme.colorScheme.error) }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text = stringResource(R.string.no_blocked_calls)) }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(history, key = { it.id }) { call ->
                    val date = remember(call.timestamp) { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(call.timestamp)) }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = call.phoneNumber, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(text = date, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BlockedListScreen(
    blockedList: List<PermanentBlockedNumber>,
    onUnblock: (PermanentBlockedNumber) -> Unit,
    onUnblockAll: () -> Unit,
    onWhitelist: (PermanentBlockedNumber) -> Unit
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Block,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.blocked_numbers_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (blockedList.isNotEmpty()) {
                Button(
                    onClick = onUnblockAll,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                ) {
                    Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.unblock_all), fontSize = 12.sp)
                }
            }
        }
        
        OutlinedButton(
            onClick = {
                val intent = Intent("android.provider.action.MANAGE_BLOCKED_NUMBERS")
                try {
                    context.startActivity(intent)
                } catch (_: Exception) {
                    val fallbackIntent = Intent(Intent.ACTION_DIAL)
                    context.startActivity(fallbackIntent)
                    Toast.makeText(context, "Please find 'Blocked Numbers' in your phone settings", Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        ) {
            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.open_system_settings), fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (blockedList.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) { 
                Text(text = stringResource(R.string.no_blocked_numbers), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) 
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(blockedList, key = { it.id }) { number ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(text = number.phoneNumber, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = { onWhitelist(number) }) { Icon(Icons.Default.Check, null, tint = Color(0xFF4CAF50)) }
                            IconButton(onClick = { onUnblock(number) }) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TroubleshootingScreen(onShowPrivacyPolicy: () -> Unit = {}) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.support_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = stringResource(R.string.support_desc),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        val deviceInfoHeader = stringResource(R.string.device_info_header)
        val supportSubject = stringResource(R.string.support_email_subject)
        
        Button(
            onClick = {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val appVersion = "${packageInfo.versionName} (${packageInfo.longVersionCode})"
                
                val deviceInfo = """
                    $deviceInfoHeader
                    App Version: $appVersion
                    Model: ${Build.MANUFACTURER} ${Build.MODEL}
                    Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
                """.trimIndent()

                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = "mailto:".toUri()
                    putExtra(Intent.EXTRA_EMAIL, arrayOf("prismaticamedia@gmail.com"))
                    putExtra(Intent.EXTRA_SUBJECT, supportSubject)
                    putExtra(Intent.EXTRA_TEXT, deviceInfo)
                }
                try {
                    context.startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(context, "No email app found", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Icon(Icons.Default.Email, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.contact_btn))
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onShowPrivacyPolicy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.PrivacyTip, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.privacy_policy_title))
        }
    }
}

@Composable
fun WhitelistScreen(whitelist: List<WhitelistedNumber>, onRemove: (WhitelistedNumber) -> Unit, onAddManual: (String) -> Unit) {
    var textState by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.whitelist_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }
        
        OutlinedTextField(
            value = textState,
            onValueChange = { textState = it },
            label = { Text(stringResource(R.string.add_number_hint)) },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = { if (textState.isNotBlank()) { onAddManual(textState); textState = "" } }) {
                    Icon(Icons.Default.Add, null)
                }
            }
        )
        
        Spacer(modifier = Modifier.height(24.dp))

        if (whitelist.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text = stringResource(R.string.whitelist_empty)) }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(whitelist, key = { it.id }) { number ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(text = number.phoneNumber, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = { onRemove(number) }) { Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(R.string.privacy_policy_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.privacy_policy_content),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.close_btn))
            }
        }
    )
}

private fun checkPermission(context: Context, permission: String): Boolean {
    return try {
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) {
        false
    }
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return try {
        pm.isIgnoringBatteryOptimizations(context.packageName)
    } catch (_: Exception) {
        false
    }
}

private fun checkRoleHeld(context: Context): Boolean {
    val roleManager = (context.getSystemService(Context.ROLE_SERVICE) as? RoleManager) ?: return false
    return try {
        roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    } catch (_: Exception) {
        false
    }
}

@Preview(showBackground = true, name = "Protection Screen - Active")
@Composable
private fun MainScreenActivePreview() {
    BlockyTheme {
        BlockyScreen(
            isRoleHeldInitial = true,
            onRoleChanged = {},
            isEnabledInitial = true,
            onEnabledChanged = {},
            currentLang = "en",
            onLanguageChanged = {},
            blockedCount = 12
        )
    }
}

@Preview(showBackground = true, name = "Onboarding Screen")
@Composable
private fun OnboardingScreenPreview() {
    BlockyTheme {
        OnboardingScreen(
            currentLang = "en",
            onLanguageChanged = {},
            onCompleted = {}
        )
    }
}
