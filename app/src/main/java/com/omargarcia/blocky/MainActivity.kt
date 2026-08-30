@file:Suppress("FunctionName")

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
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import kotlin.math.roundToInt
import com.omargarcia.blocky.data.*
import com.omargarcia.blocky.ui.theme.BlockyTheme
import com.omargarcia.blocky.ui.theme.VT323Font
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

val LocalSoundManager = compositionLocalOf<SoundManager?> { null }

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

    val soundManager = remember { SoundManager(context) }
    var isSoundEnabled by remember { mutableStateOf(settingsManager.isSoundEnabled) }

    DisposableEffect(Unit) {
        onDispose {
            soundManager.release()
        }
    }

    var isOnboardingCompleted by remember { mutableStateOf(settingsManager.isOnboardingCompleted) }
    var roleHeldState by remember { mutableStateOf(checkRoleHeld(context)) }
    var isEnabled by remember { mutableStateOf(settingsManager.isBlockingEnabled) }
    var currentLang by remember { mutableStateOf(settingsManager.languageCode) }
    var repeatCallThreshold by remember { mutableIntStateOf(settingsManager.repeatCallThreshold) }
    
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

    val blockedCount by blockLogDao.getTotalBlockedCount().collectAsState(initial = 0)
    val blockedCalls by blockLogDao.getAll().collectAsState(initial = emptyList())
    val whitelist by whitelistDao.getAllWhitelisted().collectAsState(initial = emptyList())

    CompositionLocalProvider(LocalSoundManager provides soundManager) {
        Crossfade(targetState = isOnboardingCompleted, label = "ScreenTransition") { completed ->
            if (completed) {
                MainContent(
                    roleHeld = roleHeldState,
                    isEnabled = isEnabled,
                    isSoundEnabled = isSoundEnabled,
                    blockedCount = blockedCount,
                    blockedList = blockedCalls,
                    whitelist = whitelist,
                    currentLang = currentLang,
                    repeatCallThreshold = repeatCallThreshold,
                    onRoleChanged = { roleHeldState = it },
                    onEnabledChanged = {
                        isEnabled = it
                        settingsManager.isBlockingEnabled = it
                    },
                    onSoundEnabledChanged = {
                        isSoundEnabled = it
                        settingsManager.isSoundEnabled = it
                        if (!it) {
                            soundManager.stopMusic()
                        }
                    },
                    onLanguageChanged = { lang ->
                        settingsManager.languageCode = lang
                        currentLang = lang
                        (context as? ComponentActivity)?.recreate()
                    },
                    onThresholdChanged = { threshold ->
                        repeatCallThreshold = threshold
                        settingsManager.repeatCallThreshold = threshold
                    },
                onUnblockNumber = { numberObj ->
                    scope.launch { 
                        blockLogDao.delete(numberObj)
                        permanentBlockDao.deleteByNumber(numberObj.phoneNumber)
                        unblockedDao.insert(UnblockedNumber(phoneNumber = numberObj.phoneNumber))
                        Toast.makeText(context, R.string.toast_deleted, Toast.LENGTH_SHORT).show()
                    }
                },
                onUnblockAll = {
                    scope.launch {
                        blockedCalls.forEach {
                            unblockedDao.insert(UnblockedNumber(phoneNumber = it.phoneNumber))
                        }
                        blockLogDao.clearAll()
                        permanentBlockDao.clearAll()
                        Toast.makeText(context, R.string.toast_unblocked_all, Toast.LENGTH_SHORT).show()
                    }
                },
                onAddToWhitelistFromBlocked = { numberObj ->
                    scope.launch {
                        blockLogDao.delete(numberObj)
                        permanentBlockDao.deleteByNumber(numberObj.phoneNumber)
                        unblockedDao.insert(UnblockedNumber(phoneNumber = numberObj.phoneNumber))
                        whitelistDao.insert(WhitelistedNumber(phoneNumber = numberObj.phoneNumber))
                        Toast.makeText(context, R.string.toast_whitelisted, Toast.LENGTH_SHORT).show()
                    }
                },
                onAddToBlockedFromWhitelist = { numberObj ->
                    scope.launch {
                        whitelistDao.delete(numberObj)
                        unblockedDao.deleteByNumber(numberObj.phoneNumber)
                        blockLogDao.insert(BlockedCall(phoneNumber = numberObj.phoneNumber))
                        permanentBlockDao.insert(PermanentBlockedNumber(phoneNumber = numberObj.phoneNumber))
                        Toast.makeText(context, R.string.toast_blocked, Toast.LENGTH_SHORT).show()
                    }
                },
                onDeleteBlockedPermanent = { numberObj ->
                    scope.launch {
                        blockLogDao.delete(numberObj)
                        permanentBlockDao.deleteByNumber(numberObj.phoneNumber)
                        Toast.makeText(context, R.string.toast_deleted, Toast.LENGTH_SHORT).show()
                    }
                },
                onDeleteWhitelistPermanent = { numberObj ->
                    scope.launch {
                        whitelistDao.delete(numberObj)
                        Toast.makeText(context, R.string.toast_deleted, Toast.LENGTH_SHORT).show()
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
}

@Composable
fun OnboardingScreen(
    currentLang: String,
    onLanguageChanged: (String) -> Unit,
    onCompleted: () -> Unit
) {
    val context = LocalContext.current
    val isInPreview = LocalInspectionMode.current
    val soundManager = LocalSoundManager.current
    
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
                    soundManager?.playClick()
                    showContactsDisclosure = false
                    permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                }) {
                    Text(stringResource(R.string.disclosure_accept_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    soundManager?.playClick()
                    showContactsDisclosure = false
                }) {
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
                    soundManager?.playClick()
                    showNotificationDisclosure = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }) {
                    Text(stringResource(R.string.disclosure_accept_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    soundManager?.playClick()
                    showNotificationDisclosure = false
                }) {
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
                    FilterChip(
                        selected = currentLang == "en",
                        onClick = {
                            soundManager?.playClick()
                            onLanguageChanged("en")
                        },
                        label = { Text(stringResource(R.string.lang_english)) }
                    )
                    FilterChip(
                        selected = currentLang == "es",
                        onClick = {
                            soundManager?.playClick()
                            onLanguageChanged("es")
                        },
                        label = { Text(stringResource(R.string.lang_spanish)) }
                    )
                }
                IconButton(onClick = {
                    soundManager?.playClick()
                    showPrivacyPolicyModal = true
                }) {
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
                soundManager?.playClick()
                val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                roleManager?.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)?.let { roleLauncher.launch(it) }
            }

            OnboardingStep(
                title = stringResource(R.string.step_contacts_title),
                desc = stringResource(R.string.step_contacts_desc),
                isDone = contactsState,
                btnText = stringResource(R.string.grant_permission),
            ) { 
                soundManager?.playClick()
                showContactsDisclosure = true
            }

            OnboardingStep(
                title = stringResource(R.string.step_notification_title),
                desc = stringResource(R.string.step_notification_desc),
                isDone = notificationState,
                btnText = stringResource(R.string.grant_permission),
            ) { 
                soundManager?.playClick()
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
                soundManager?.playClick()
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            
            Button(
                onClick = {
                    soundManager?.playClick()
                    soundManager?.playOnboardingTheme()
                    onCompleted()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = roleState
            ) {
                Text(text = if (roleState && contactsState) stringResource(R.string.setup_complete) else stringResource(R.string.get_started))
            }

            TextButton(onClick = {
                soundManager?.playClick()
                showPrivacyPolicyModal = true
            }) {
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

@Composable
fun rememberParallaxOffset(maxOffsetPx: Float = 45f): State<Offset> {
    val context = LocalContext.current
    val isInPreview = LocalInspectionMode.current
    val targetOffset = remember { mutableStateOf(Offset.Zero) }

    if (!isInPreview) {
        DisposableEffect(Unit) {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            val accelSensor = if (rotationSensor == null) {
                sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            } else null

            val listener = object : SensorEventListener {
                private val rotationMatrix = FloatArray(9)
                private val orientation = FloatArray(3)
                private var baselinePitch = Float.NaN
                private var baselineRoll = Float.NaN

                override fun onSensorChanged(event: SensorEvent) {
                    when (event.sensor.type) {
                        Sensor.TYPE_ROTATION_VECTOR -> {
                            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                            SensorManager.getOrientation(rotationMatrix, orientation)
                            val pitch = orientation[1]
                            val roll = orientation[2]

                            if (baselinePitch.isNaN()) {
                                baselinePitch = pitch
                                baselineRoll = roll
                            } else {
                                baselinePitch += (pitch - baselinePitch) * 0.003f
                                baselineRoll += (roll - baselineRoll) * 0.003f
                            }

                            val diffRoll = (roll - baselineRoll).coerceIn(-0.45f, 0.45f)
                            val diffPitch = (pitch - baselinePitch).coerceIn(-0.45f, 0.45f)

                            val x = -diffRoll * (maxOffsetPx / 0.45f)
                            val y = -diffPitch * (maxOffsetPx / 0.45f)
                            targetOffset.value = Offset(x, y)
                        }
                        Sensor.TYPE_ACCELEROMETER -> {
                            val rawX = event.values[0]
                            val rawY = event.values[1]

                            if (baselineRoll.isNaN()) {
                                baselineRoll = rawX
                                baselinePitch = rawY
                            } else {
                                baselineRoll += (rawX - baselineRoll) * 0.003f
                                baselinePitch += (rawY - baselinePitch) * 0.003f
                            }

                            val diffX = (rawX - baselineRoll).coerceIn(-4.0f, 4.0f)
                            val diffY = (rawY - baselinePitch).coerceIn(-4.0f, 4.0f)

                            val x = diffX * (maxOffsetPx / 4.0f)
                            val y = -diffY * (maxOffsetPx / 4.0f)
                            targetOffset.value = Offset(x, y)
                        }
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }

            val activeSensor = rotationSensor ?: accelSensor
            if (activeSensor != null) {
                sensorManager?.registerListener(listener, activeSensor, SensorManager.SENSOR_DELAY_GAME)
            }

            onDispose {
                sensorManager?.unregisterListener(listener)
            }
        }
    }

    return animateOffsetAsState(
        targetValue = targetOffset.value,
        animationSpec = spring(
            stiffness = Spring.StiffnessLow,
            dampingRatio = Spring.DampingRatioNoBouncy
        ),
        label = "ParallaxOffset"
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(
    roleHeld: Boolean,
    isEnabled: Boolean,
    isSoundEnabled: Boolean,
    blockedCount: Int,
    blockedList: List<BlockedCall>,
    whitelist: List<WhitelistedNumber>,
    currentLang: String,
    repeatCallThreshold: Int = 1,
    onRoleChanged: (Boolean) -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
    onSoundEnabledChanged: (Boolean) -> Unit,
    onLanguageChanged: (String) -> Unit,
    onThresholdChanged: (Int) -> Unit = {},
    onUnblockNumber: (BlockedCall) -> Unit,
    onUnblockAll: () -> Unit,
    onAddToWhitelistFromBlocked: (BlockedCall) -> Unit,
    onAddToBlockedFromWhitelist: (WhitelistedNumber) -> Unit,
    onDeleteBlockedPermanent: (BlockedCall) -> Unit,
    onDeleteWhitelistPermanent: (WhitelistedNumber) -> Unit,
    onRemoveFromWhitelist: (WhitelistedNumber) -> Unit,
    onFinishOnboarding: () -> Unit,
) {
    val parallaxOffset by rememberParallaxOffset(maxOffsetPx = 45f)
    var selectedTab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val isInPreview = LocalInspectionMode.current
    val soundManager = LocalSoundManager.current
    var showPrivacyPolicyModal by remember { mutableStateOf(false) }
    
    val isSetupIncomplete = !roleHeld || (!isInPreview && !checkPermission(context, Manifest.permission.READ_CONTACTS))

    if (showPrivacyPolicyModal) {
        PrivacyPolicyDialog(onDismiss = { showPrivacyPolicyModal = false })
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Image(
            painter = painterResource(R.drawable.bg_app_pattern),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .scale(1.22f)
                .offset { IntOffset(parallaxOffset.x.roundToInt(), parallaxOffset.y.roundToInt()) },
            alpha = 0.09f
        )

        Scaffold(
            containerColor = Color.Transparent,
            modifier = Modifier.fillMaxSize(),
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    ),
                    title = {
                        Text(
                            text = stringResource(R.string.app_name),
                            fontFamily = VT323Font,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    },
                navigationIcon = {
                    IconButton(onClick = {
                        val newSoundState = !isSoundEnabled
                        onSoundEnabledChanged(newSoundState)
                        if (newSoundState) {
                            soundManager?.playClick()
                        }
                    }) {
                        Icon(
                            imageVector = if (isSoundEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = stringResource(if (isSoundEnabled) R.string.sound_enabled else R.string.sound_disabled),
                            tint = if (isSoundEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        soundManager?.playClick()
                        showPrivacyPolicyModal = true
                    }) {
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
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Shield, null) },
                    label = { Text(stringResource(R.string.protection_tab)) },
                    selected = selectedTab == 0,
                    onClick = {
                        soundManager?.playClick()
                        selectedTab = 0
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Block, null) },
                    label = { Text(stringResource(R.string.blocked_list_tab)) },
                    selected = selectedTab == 1,
                    onClick = {
                        soundManager?.playClick()
                        selectedTab = 1
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.AutoMirrored.Filled.FormatListBulleted, null) },
                    label = { Text(stringResource(R.string.whitelist_tab)) },
                    selected = selectedTab == 2,
                    onClick = {
                        soundManager?.playClick()
                        selectedTab = 2
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.AutoMirrored.Filled.Help, null) },
                    label = { Text(stringResource(R.string.support_tab)) },
                    selected = selectedTab == 3,
                    onClick = {
                        soundManager?.playClick()
                        selectedTab = 3
                    }
                )
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
                                TextButton(onClick = {
                                    soundManager?.playClick()
                                    onFinishOnboarding()
                                }) {
                                    Text(stringResource(R.string.finish_setup_btn))
                                }
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
                                repeatCallThreshold = repeatCallThreshold,
                                onThresholdChanged = onThresholdChanged,
                            )
                            1 -> BlockedListScreen(
                                blockedList = blockedList,
                                onUnblock = onUnblockNumber,
                                onUnblockAll = onUnblockAll,
                                onWhitelist = onAddToWhitelistFromBlocked,
                                onDeletePermanent = onDeleteBlockedPermanent
                            )
                            2 -> WhitelistScreen(
                                whitelist = whitelist,
                                onRemove = onRemoveFromWhitelist,
                                onDeletePermanent = onDeleteWhitelistPermanent,
                                onBlockNumber = onAddToBlockedFromWhitelist
                            )
                            3 -> TroubleshootingScreen(onShowPrivacyPolicy = {
                                soundManager?.playClick()
                                showPrivacyPolicyModal = true
                            })
                        }
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
    repeatCallThreshold: Int = 1,
    onThresholdChanged: (Int) -> Unit = {},
) {
    val context = LocalContext.current
    val soundManager = LocalSoundManager.current
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
        onRoleChanged(checkRoleHeld(context))
    }
    var showCustomThresholdDialog by remember { mutableStateOf(false) }
    var customInputText by remember { mutableStateOf("") }

    if (showCustomThresholdDialog) {
        AlertDialog(
            onDismissRequest = { showCustomThresholdDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.custom_threshold_dialog_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.custom_threshold_dialog_desc),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = customInputText,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() } && input.length <= 3) {
                                customInputText = input
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        placeholder = { Text(stringResource(R.string.custom_threshold_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val num = customInputText.toIntOrNull()
                        if (num != null && num >= 2) {
                            soundManager?.playClick()
                            onThresholdChanged(num)
                            showCustomThresholdDialog = false
                        } else {
                            Toast.makeText(context, R.string.toast_invalid_threshold, Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text(stringResource(R.string.save_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    soundManager?.playClick()
                    showCustomThresholdDialog = false
                }) {
                    Text(stringResource(R.string.cancel_btn))
                }
            }
        )
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section: Logo + App Name + Subtitle
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_blocky_logo),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = VT323Font,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (isRoleHeldInitial) stringResource(R.string.protection_active) else stringResource(R.string.protection_inactive),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
                )
            }

            // Hero Center Section: Big Blocked Counter
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = blockedCount.toString(),
                    fontSize = 58.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = VT323Font,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = stringResource(R.string.calls_blocked_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)
                )
            }

            // Controls Section: Combined Shield Status & Switch Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isRoleHeldInitial && isEnabledInitial) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = if (isRoleHeldInitial && isEnabledInitial) stringResource(R.string.shield_active) else stringResource(R.string.shield_down),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when {
                                !isRoleHeldInitial -> stringResource(R.string.shield_down_desc)
                                !isEnabledInitial -> stringResource(R.string.service_disabled)
                                else -> stringResource(R.string.shield_active_desc)
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (isRoleHeldInitial) {
                        Switch(
                            checked = isEnabledInitial,
                            onCheckedChange = {
                                soundManager?.playClick()
                                onEnabledChanged(it)
                            }
                        )
                    } else {
                        Button(
                            onClick = {
                                soundManager?.playClick()
                                val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
                                roleManager?.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)?.let { roleLauncher.launch(it) }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(text = stringResource(R.string.grant_permission), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // Allowed Calls Threshold Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.repeat_call_threshold_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = stringResource(R.string.repeat_call_threshold_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Always Block chip
                        FilterChip(
                            selected = repeatCallThreshold <= 1,
                            onClick = {
                                if (repeatCallThreshold != 1) {
                                    soundManager?.playClick()
                                    onThresholdChanged(1)
                                }
                            },
                            leadingIcon = if (repeatCallThreshold <= 1) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            } else null,
                            label = { Text(stringResource(R.string.threshold_always_block), style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )

                        // Custom write box button
                        val isCustomActive = repeatCallThreshold > 1
                        FilterChip(
                            selected = isCustomActive,
                            onClick = {
                                soundManager?.playClick()
                                customInputText = if (repeatCallThreshold > 1) repeatCallThreshold.toString() else "2"
                                showCustomThresholdDialog = true
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isCustomActive) Icons.Default.Check else Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = if (isCustomActive) {
                                        stringResource(R.string.threshold_custom_btn, repeatCallThreshold)
                                    } else {
                                        stringResource(R.string.threshold_set_custom_btn)
                                    },
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Bottom Section: Language Selection Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.language_selection) + ":",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = currentLang == "en",
                        onClick = {
                            soundManager?.playClick()
                            onLanguageChanged("en")
                        },
                        label = { Text(stringResource(R.string.lang_english), style = MaterialTheme.typography.labelSmall) }
                    )
                    FilterChip(
                        selected = currentLang == "es",
                        onClick = {
                            soundManager?.playClick()
                            onLanguageChanged("es")
                        },
                        label = { Text(stringResource(R.string.lang_spanish), style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    }
}

@Composable
fun BlockedListScreen(
    blockedList: List<BlockedCall>,
    onUnblock: (BlockedCall) -> Unit,
    onUnblockAll: () -> Unit,
    onWhitelist: (BlockedCall) -> Unit,
    onDeletePermanent: (BlockedCall) -> Unit
) {
    val context = LocalContext.current
    val soundManager = LocalSoundManager.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedNumberForDetails by remember { mutableStateOf<BlockedCall?>(null) }
    var showUnblockAllDialog by remember { mutableStateOf(false) }

    if (showUnblockAllDialog) {
        AlertDialog(
            onDismissRequest = { showUnblockAllDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.unblock_all_dialog_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.unblock_all_dialog_desc)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        soundManager?.playClick()
                        showUnblockAllDialog = false
                        onUnblockAll()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.unblock_all_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    soundManager?.playClick()
                    showUnblockAllDialog = false
                }) {
                    Text(stringResource(R.string.cancel_btn))
                }
            }
        )
    }

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
                tint = Color.White
            )
            Text(
                text = stringResource(R.string.blocked_numbers_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = {
                        soundManager?.playClick()
                        searchQuery = ""
                    }) {
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
                onClick = {
                    soundManager?.playClick()
                    showUnblockAllDialog = true
                },
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
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        } else if (filteredList.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.no_results_found),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = Color.White.copy(alpha = 0.8f)
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
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp)
                        ) {
                            Text(
                                text = dateHeader,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                    items(itemsInGroup, key = { it.id }) { number ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    soundManager?.playClick()
                                    selectedNumberForDetails = number
                                }
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
                                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Default),
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }
                                IconButton(onClick = {
                                    soundManager?.playClick()
                                    onWhitelist(number)
                                }) {
                                    Icon(Icons.Default.Check, contentDescription = stringResource(R.string.action_whitelist), tint = Color(0xFF4CAF50))
                                }
                                IconButton(onClick = {
                                    soundManager?.playClick()
                                    onUnblock(number)
                                }) {
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
    onBlockNumber: (WhitelistedNumber) -> Unit
) {
    val context = LocalContext.current
    val soundManager = LocalSoundManager.current
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
                tint = Color.White
            )
            Text(
                text = stringResource(R.string.whitelist_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = {
                        soundManager?.playClick()
                        searchQuery = ""
                    }) {
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
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        } else if (filteredList.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.no_results_found),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = Color.White.copy(alpha = 0.8f)
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
                            .clickable {
                                soundManager?.playClick()
                                selectedNumberForDetails = number
                            }
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
                                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Default),
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                            IconButton(onClick = {
                                soundManager?.playClick()
                                onRemove(number)
                            }) {
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
    val soundManager = LocalSoundManager.current
    val metadataHelper = remember { PhoneNumberMetadataHelper(context) }
    val details = remember(phoneNumber) { metadataHelper.getNumberDetails(phoneNumber) }
    val dateStr = remember(timestamp) {
        SimpleDateFormat("EEE, MMM dd, yyyy • HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.delete_permanent_dialog_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.delete_permanent_dialog_desc)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        soundManager?.playClick()
                        showDeleteConfirmDialog = false
                        onDeletePermanent()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete_permanent_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    soundManager?.playClick()
                    showDeleteConfirmDialog = false
                }) {
                    Text(stringResource(R.string.cancel_btn))
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = details.flagEmoji,
                fontSize = 38.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Text(
                text = details.formattedNational.ifBlank { details.rawNumber },
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Default),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            if (details.formattedInternational.isNotBlank() && details.formattedInternational != details.formattedNational) {
                Text(
                    text = details.formattedInternational,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Default),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Text(
                text = dateStr,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons
            Button(
                onClick = {
                    soundManager?.playClick()
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

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedButton(
                onClick = {
                    soundManager?.playClick()
                    onToggleList()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (isBlocked) Icons.Default.CheckCircle else Icons.Default.Block,
                    contentDescription = null,
                    tint = if (isBlocked) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isBlocked) stringResource(R.string.action_move_to_whitelist) else stringResource(R.string.action_move_to_blocked)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            OutlinedButton(
                onClick = {
                    soundManager?.playClick()
                    onRemoveFromList()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.RemoveCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_remove_from_list))
            }

            Spacer(modifier = Modifier.height(6.dp))

            TextButton(
                onClick = {
                    soundManager?.playClick()
                    showDeleteConfirmDialog = true
                },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_delete_permanent))
            }

            Spacer(modifier = Modifier.height(12.dp))
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
            color = Color.White,
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

        Spacer(modifier = Modifier.height(36.dp))

        val isInPreview = LocalInspectionMode.current
        val appVersionStr = remember(isInPreview) {
            if (isInPreview) {
                "v1.0.14"
            } else {
                try {
                    val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                    "v${pInfo.versionName}"
                } catch (_: Exception) {
                    "v1.0.14"
                }
            }
        }

        Text(
            text = "Blocky $appVersionStr",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 24.dp)
        )
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
    val intent = Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI).apply {
        putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumber)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            val fallbackIntent = Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
                type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
                putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumber)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
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

// ==========================================
// 🎨 Jetpack Compose Android Studio Previews
// ==========================================

@Preview(showBackground = true, name = "1. Onboarding Screen")
@Composable
fun OnboardingPreview() {
    BlockyTheme {
        OnboardingScreen(
            currentLang = "en",
            onLanguageChanged = {},
            onCompleted = {}
        )
    }
}

@Preview(showBackground = true, name = "2. Protection Active")
@Composable
fun BlockyScreenActivePreview() {
    BlockyTheme {
        BlockyScreen(
            isRoleHeldInitial = true,
            onRoleChanged = {},
            isEnabledInitial = true,
            onEnabledChanged = {},
            currentLang = "en",
            onLanguageChanged = {},
            blockedCount = 42,
            repeatCallThreshold = 2,
        )
    }
}

@Preview(showBackground = true, name = "3. Protection Inactive")
@Composable
fun BlockyScreenInactivePreview() {
    BlockyTheme {
        BlockyScreen(
            isRoleHeldInitial = false,
            onRoleChanged = {},
            isEnabledInitial = false,
            onEnabledChanged = {},
            currentLang = "es",
            onLanguageChanged = {},
            blockedCount = 0
        )
    }
}

@Preview(showBackground = true, name = "4. Blocked Numbers List")
@Composable
fun BlockedListPreview() {
    val mockBlocked = listOf(
        BlockedCall(id = 1, phoneNumber = "+1 555-0101"),
        BlockedCall(id = 2, phoneNumber = "+1 555-0102"),
        BlockedCall(id = 3, phoneNumber = "Private / Unknown")
    )
    BlockyTheme {
        BlockedListScreen(
            blockedList = mockBlocked,
            onUnblock = {},
            onUnblockAll = {},
            onWhitelist = {},
            onDeletePermanent = {}
        )
    }
}

@Preview(showBackground = true, name = "5. Whitelist Screen")
@Composable
fun WhitelistPreview() {
    val mockWhitelist = listOf(
        WhitelistedNumber(id = 1, phoneNumber = "+1 555-9999"),
        WhitelistedNumber(id = 2, phoneNumber = "+1 555-1234")
    )
    BlockyTheme {
        WhitelistScreen(
            whitelist = mockWhitelist,
            onRemove = {},
            onDeletePermanent = {},
            onBlockNumber = {}
        )
    }
}

@Preview(showBackground = true, name = "6. Troubleshooting & Support")
@Composable
fun TroubleshootingPreview() {
    BlockyTheme {
        TroubleshootingScreen()
    }
}
