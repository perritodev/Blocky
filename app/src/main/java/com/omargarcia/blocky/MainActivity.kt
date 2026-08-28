package com.omargarcia.blocky

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.omargarcia.blocky.data.*
import com.omargarcia.blocky.ui.theme.BlockyTheme
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
    val permanentBlockedList by permanentBlockDao.getAll().collectAsState(initial = emptyList())
    val whitelist by whitelistDao.getAllWhitelisted().collectAsState(initial = emptyList())

    Crossfade(targetState = isOnboardingCompleted, label = "ScreenTransition") { completed ->
        if (completed) {
            MainContent(
                roleHeld = roleHeldState,
                isEnabled = isEnabled,
                blockedCount = blockedCount,
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
                onAddToBlockedFromWhitelist = { numberObj ->
                    scope.launch {
                        whitelistDao.delete(numberObj)
                        unblockedDao.deleteByNumber(numberObj.phoneNumber)
                        permanentBlockDao.insert(PermanentBlockedNumber(phoneNumber = numberObj.phoneNumber))
                        Toast.makeText(context, R.string.toast_blocked, Toast.LENGTH_SHORT).show()
                    }
                },
                onDeleteBlockedPermanent = { numberObj ->
                    scope.launch {
                        permanentBlockDao.delete(numberObj)
                        Toast.makeText(context, R.string.toast_deleted, Toast.LENGTH_SHORT).show()
                    }
                },
                onDeleteWhitelistPermanent = { numberObj ->
                    scope.launch {
                        whitelistDao.delete(numberObj)
                        Toast.makeText(context, R.string.toast_deleted, Toast.LENGTH_SHORT).show()
                    }
                },
                onAddToBlockedManual = { number ->
                    scope.launch {
                        val trimmed = number.trim()
                        if (trimmed.isNotBlank()) {
                            unblockedDao.deleteByNumber(trimmed)
                            whitelistDao.deleteByNumber(trimmed)
                            permanentBlockDao.insert(PermanentBlockedNumber(phoneNumber = trimmed))
                            Toast.makeText(context, R.string.toast_blocked, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onAddToWhitelistManual = { number ->
                    scope.launch {
                        val trimmed = number.trim()
                        if (trimmed.isNotBlank()) {
                            unblockedDao.deleteByNumber(trimmed)
                            permanentBlockDao.deleteByNumber(trimmed)
                            whitelistDao.insert(WhitelistedNumber(phoneNumber = trimmed))
                            Toast.makeText(context, R.string.toast_whitelisted, Toast.LENGTH_SHORT).show()
                        }
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
    permanentBlockedList: List<PermanentBlockedNumber>,
    whitelist: List<WhitelistedNumber>,
    currentLang: String,
    onRoleChanged: (Boolean) -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
    onLanguageChanged: (String) -> Unit,
    onUnblockNumber: (PermanentBlockedNumber) -> Unit,
    onUnblockAll: () -> Unit,
    onAddToWhitelistFromBlocked: (PermanentBlockedNumber) -> Unit,
    onAddToBlockedFromWhitelist: (WhitelistedNumber) -> Unit,
    onDeleteBlockedPermanent: (PermanentBlockedNumber) -> Unit,
    onDeleteWhitelistPermanent: (WhitelistedNumber) -> Unit,
    onAddToBlockedManual: (String) -> Unit,
    onAddToWhitelistManual: (String) -> Unit,
    onRemoveFromWhitelist: (WhitelistedNumber) -> Unit,
    onFinishOnboarding: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val isInPreview = LocalInspectionMode.current
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
                                onWhitelist = onAddToWhitelistFromBlocked,
                                onDeletePermanent = onDeleteBlockedPermanent,
                                onAddManual = onAddToBlockedManual
                            )
                            2 -> WhitelistScreen(
                                whitelist = whitelist,
                                onRemove = onRemoveFromWhitelist,
                                onDeletePermanent = onDeleteWhitelistPermanent,
                                onBlockNumber = onAddToBlockedFromWhitelist,
                                onAddManual = onAddToWhitelistManual
                            )
                            3 -> TroubleshootingScreen(onShowPrivacyPolicy = { showPrivacyPolicyModal = true })
                        }
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

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
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
}

@Composable
fun BlockedListScreen(
    blockedList: List<PermanentBlockedNumber>,
    onUnblock: (PermanentBlockedNumber) -> Unit,
    onUnblockAll: () -> Unit,
    onWhitelist: (PermanentBlockedNumber) -> Unit,
    onDeletePermanent: (PermanentBlockedNumber) -> Unit,
    onAddManual: (String) -> Unit
) {
    val context = LocalContext.current
    var addNumberText by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedNumberForDetails by remember { mutableStateOf<PermanentBlockedNumber?>(null) }

    val filteredList = remember(blockedList, searchQuery) {
        if (searchQuery.isBlank()) {
            blockedList
        } else {
            blockedList.filter { matchesSearchQuery(context, it.phoneNumber, it.timestamp, searchQuery) }
        }
    }

    val groupedByDate = remember(filteredList) {
        filteredList.groupBy { getDateGroupTitle(context, it.timestamp) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.padding(bottom = 12.dp),
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

        // Add Number Input
        OutlinedTextField(
            value = addNumberText,
            onValueChange = { addNumberText = it },
            label = { Text(stringResource(R.string.add_number_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = {
                    if (addNumberText.isNotBlank()) {
                        onAddManual(addNumberText)
                        addNumberText = ""
                    }
                }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_btn))
                }
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_search))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (blockedList.isNotEmpty() && searchQuery.isBlank()) {
            Button(
                onClick = onUnblockAll,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(Icons.Default.DeleteSweep, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.unblock_all), fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (blockedList.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.no_blocked_numbers),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (filteredList.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.no_results_found),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                groupedByDate.forEach { (dateHeader, itemsInGroup) ->
                    item(key = "header_$dateHeader") {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp)
                        ) {
                            Text(
                                text = dateHeader,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                    items(itemsInGroup, key = { it.id }) { number ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedNumberForDetails = number }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Block,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = number.phoneNumber,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                IconButton(onClick = { onWhitelist(number) }) {
                                    Icon(Icons.Default.Check, contentDescription = stringResource(R.string.action_whitelist), tint = Color(0xFF4CAF50))
                                }
                                IconButton(onClick = { onUnblock(number) }) {
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_remove_from_list), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedNumberForDetails?.let { number ->
        CallerDetailBottomSheet(
            phoneNumber = number.phoneNumber,
            timestamp = number.timestamp,
            isBlocked = true,
            onDismiss = { selectedNumberForDetails = null },
            onRemoveFromList = { onUnblock(number) },
            onDeletePermanent = { onDeletePermanent(number) },
            onToggleList = { onWhitelist(number) },
            onAddToContacts = { numToSave -> launchAddToContacts(context, numToSave) }
        )
    }
}

@Composable
fun WhitelistScreen(
    whitelist: List<WhitelistedNumber>,
    onRemove: (WhitelistedNumber) -> Unit,
    onDeletePermanent: (WhitelistedNumber) -> Unit,
    onBlockNumber: (WhitelistedNumber) -> Unit,
    onAddManual: (String) -> Unit
) {
    val context = LocalContext.current
    var addNumberText by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedNumberForDetails by remember { mutableStateOf<WhitelistedNumber?>(null) }

    val filteredList = remember(whitelist, searchQuery) {
        if (searchQuery.isBlank()) {
            whitelist
        } else {
            whitelist.filter { matchesSearchQuery(context, it.phoneNumber, it.timestamp, searchQuery) }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.padding(bottom = 12.dp),
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

        // Add Number Input
        OutlinedTextField(
            value = addNumberText,
            onValueChange = { addNumberText = it },
            label = { Text(stringResource(R.string.add_number_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = {
                    if (addNumberText.isNotBlank()) {
                        onAddManual(addNumberText)
                        addNumberText = ""
                    }
                }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_btn))
                }
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_search))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (whitelist.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.whitelist_empty),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (filteredList.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.no_results_found),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredList, key = { it.id }) { number ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedNumberForDetails = number }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = number.phoneNumber,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            IconButton(onClick = { onRemove(number) }) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remove_from_whitelist), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    selectedNumberForDetails?.let { number ->
        CallerDetailBottomSheet(
            phoneNumber = number.phoneNumber,
            timestamp = number.timestamp,
            isBlocked = false,
            onDismiss = { selectedNumberForDetails = null },
            onRemoveFromList = { onRemove(number) },
            onDeletePermanent = { onDeletePermanent(number) },
            onToggleList = { onBlockNumber(number) },
            onAddToContacts = { numToSave -> launchAddToContacts(context, numToSave) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallerDetailBottomSheet(
    phoneNumber: String,
    timestamp: Long,
    isBlocked: Boolean,
    onDismiss: () -> Unit,
    onRemoveFromList: () -> Unit,
    onDeletePermanent: () -> Unit,
    onToggleList: () -> Unit,
    onAddToContacts: (String) -> Unit,
) {
    val context = LocalContext.current
    val metadataHelper = remember { PhoneNumberMetadataHelper(context) }
    val details = remember(phoneNumber) { metadataHelper.getNumberDetails(phoneNumber) }
    val dateStr = remember(timestamp) {
        SimpleDateFormat("EEE, MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date(timestamp))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = details.flagEmoji,
                fontSize = 44.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = details.formattedNational.ifBlank { details.rawNumber },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            if (details.formattedInternational.isNotBlank() && details.formattedInternational != details.formattedNational) {
                Text(
                    text = details.formattedInternational,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Text(
                text = dateStr,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (details.countryName.isNotBlank()) {
                        DetailInfoRow(
                            icon = Icons.Default.Public,
                            label = stringResource(R.string.country_label),
                            value = if (details.countryCode > 0) "${details.countryName} (+${details.countryCode})" else details.countryName
                        )
                    }

                    if (details.location.isNotBlank()) {
                        DetailInfoRow(
                            icon = Icons.Default.Place,
                            label = stringResource(R.string.location_label),
                            value = details.location
                        )
                    }

                    DetailInfoRow(
                        icon = Icons.Default.PhoneAndroid,
                        label = stringResource(R.string.line_type_label),
                        value = details.numberType
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons
            Button(
                onClick = {
                    onAddToContacts(details.formattedNational.ifBlank { details.rawNumber })
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_add_to_contacts))
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onToggleList()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isBlocked) Icons.Default.Check else Icons.Default.Block,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isBlocked) stringResource(R.string.action_move_to_whitelist) else stringResource(R.string.action_move_to_blocked),
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = {
                        onRemoveFromList()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.RemoveCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_remove_from_list), fontSize = 12.sp, maxLines = 1)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = {
                    onDeletePermanent()
                    onDismiss()
                },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_delete_permanent))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun DetailInfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
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

fun getDateGroupTitle(context: Context, timestamp: Long): String {
    val now = Calendar.getInstance()
    val itemCal = Calendar.getInstance().apply { timeInMillis = timestamp }

    val isSameYear = now.get(Calendar.YEAR) == itemCal.get(Calendar.YEAR)
    val isSameDay = isSameYear && (now.get(Calendar.DAY_OF_YEAR) == itemCal.get(Calendar.DAY_OF_YEAR))
    
    if (isSameDay) {
        return context.getString(R.string.group_today)
    }

    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val isYesterday = yesterday.get(Calendar.YEAR) == itemCal.get(Calendar.YEAR) &&
            yesterday.get(Calendar.DAY_OF_YEAR) == itemCal.get(Calendar.DAY_OF_YEAR)

    if (isYesterday) {
        return context.getString(R.string.group_yesterday)
    }

    val format = if (isSameYear) {
        SimpleDateFormat("MMMM dd", Locale.getDefault())
    } else {
        SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
    }
    return format.format(Date(timestamp))
}

fun matchesSearchQuery(context: Context, phoneNumber: String, timestamp: Long, query: String): Boolean {
    if (query.isBlank()) return true
    val cleanQuery = query.trim().lowercase(Locale.getDefault())
    if (phoneNumber.lowercase(Locale.getDefault()).contains(cleanQuery)) {
        return true
    }
    val groupTitle = getDateGroupTitle(context, timestamp).lowercase(Locale.getDefault())
    if (groupTitle.contains(cleanQuery)) {
        return true
    }
    val fullDateFormat = SimpleDateFormat("yyyy-MM-dd MMMM dd yyyy", Locale.getDefault()).format(Date(timestamp)).lowercase(Locale.getDefault())
    return fullDateFormat.contains(cleanQuery)
}

fun launchAddToContacts(context: Context, phoneNumber: String) {
    val intent = Intent(Intent.ACTION_INSERT).apply {
        type = "vnd.android.cursor.dir/raw_contact"
        putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumber)
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            val fallbackIntent = Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
                type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
                putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumber)
            }
            context.startActivity(fallbackIntent)
        } catch (_: Exception) {
            Toast.makeText(context, R.string.toast_contact_intent_error, Toast.LENGTH_SHORT).show()
        }
    }
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
