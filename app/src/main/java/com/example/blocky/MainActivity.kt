package com.example.blocky

import android.app.role.RoleManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.blocky.data.AppDatabase
import com.example.blocky.data.BlockedCall
import com.example.blocky.data.SettingsManager
import com.example.blocky.ui.theme.BlockyTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
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
    val dao = remember { AppDatabase.getDatabase(context).blockedCallDao() }
    val scope = rememberCoroutineScope()

    var roleHeldState by remember { mutableStateOf(checkRoleHeld(context)) }
    var isEnabled by remember { mutableStateOf(settingsManager.isBlockingEnabled) }
    
    val blockedCount by dao.getBlockedCount().collectAsState(initial = 0)
    val history by dao.getAllBlockedCalls().collectAsState(initial = emptyList())

    MainContent(
        roleHeld = roleHeldState,
        isEnabled = isEnabled,
        blockedCount = blockedCount,
        history = history,
        onRoleChanged = { roleHeldState = it },
        onEnabledChanged = {
            isEnabled = it
            settingsManager.isBlockingEnabled = it
        },
    ) { call ->
        scope.launch {
            dao.delete(call)
            Toast.makeText(context, "Number unblocked", Toast.LENGTH_SHORT).show()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(
    roleHeld: Boolean,
    isEnabled: Boolean,
    blockedCount: Int,
    history: List<BlockedCall>,
    onRoleChanged: (Boolean) -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
    onDeleteCall: (BlockedCall) -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val isProtectionActive = roleHeld && isEnabled

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    if (isProtectionActive) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = stringResource(R.string.active_indicator),
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.GppBad,
                            contentDescription = stringResource(R.string.shield_down),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { 
                        Icon(
                            imageVector = Icons.Default.Shield, 
                            contentDescription = stringResource(R.string.content_description_shield),
                        ) 
                    },
                    label = { Text(stringResource(R.string.protection_tab)) },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                )
                NavigationBarItem(
                    icon = { 
                        Icon(
                            imageVector = Icons.Default.History, 
                            contentDescription = stringResource(R.string.content_description_history),
                        ) 
                    },
                    label = { Text(stringResource(R.string.history_tab)) },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                )
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> BlockyScreen(
                    blockedCount = blockedCount,
                    isRoleHeldInitial = roleHeld,
                    onRoleChanged = onRoleChanged,
                    isEnabledInitial = isEnabled,
                    onEnabledChanged = onEnabledChanged,
                )
                1 -> HistoryScreen(
                    history = history,
                    onDelete = onDeleteCall,
                )
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
    blockedCount: Int = 0,
) {
    val context = LocalContext.current

    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { _ ->
        val held = checkRoleHeld(context)
        onRoleChanged(held)
        if (held) {
            Toast.makeText(context, R.string.toast_call_blocking_enabled, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, R.string.toast_role_not_granted, Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (isRoleHeldInitial) stringResource(R.string.protection_active) else stringResource(R.string.protection_inactive),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )

        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = blockedCount.toString(),
            fontSize = 64.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text(
            text = stringResource(R.string.calls_blocked_label),
            style = MaterialTheme.typography.labelLarge,
        )

        Spacer(modifier = Modifier.height(48.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isRoleHeldInitial && isEnabledInitial) 
                    MaterialTheme.colorScheme.primaryContainer 
                else 
                    MaterialTheme.colorScheme.errorContainer,
            ),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (isRoleHeldInitial && isEnabledInitial) stringResource(R.string.shield_active) else stringResource(R.string.shield_down),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = when {
                        !isRoleHeldInitial -> stringResource(R.string.shield_down_desc)
                        !isEnabledInitial -> stringResource(R.string.service_disabled)
                        else -> stringResource(R.string.shield_active_desc)
                    },
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (isRoleHeldInitial) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (isEnabledInitial) stringResource(R.string.service_enabled) else stringResource(R.string.service_disabled),
                    style = MaterialTheme.typography.titleMedium,
                )
                Switch(
                    checked = isEnabledInitial,
                    onCheckedChange = { onEnabledChanged(it) },
                )
            }
        } else {
            Button(
                onClick = {
                    val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
                    if (!roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
                        val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                        roleLauncher.launch(intent)
                    } else {
                        Toast.makeText(context, R.string.toast_already_active, Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.enable_protection_btn))
            }
        }
    }
}

@Composable
fun HistoryScreen(
    history: List<BlockedCall>,
    onDelete: (BlockedCall) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.block_history_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = stringResource(R.string.no_blocked_calls), style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(
                    items = history,
                    key = { it.id },
                ) { call ->
                    HistoryItem(call = call) {
                        onDelete(call)
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryItem(
    call: BlockedCall,
    onDelete: () -> Unit,
) {
    val date = remember(call.timestamp) {
        SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(call.timestamp))
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = call.phoneNumber,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.blocked_status),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = date,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Unblock number",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun BlockyScreenPreview() {
    BlockyTheme {
        BlockyScreen(
            isRoleHeldInitial = false, 
            onRoleChanged = {},
            isEnabledInitial = true,
            onEnabledChanged = {},
            blockedCount = 12,
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun HistoryScreenPreview() {
    BlockyTheme {
        HistoryScreen(
            history = listOf(
                BlockedCall(id = 1, phoneNumber = "+1 234 567 890", timestamp = System.currentTimeMillis()),
                BlockedCall(id = 2, phoneNumber = "Private Number", timestamp = System.currentTimeMillis() - 3600000),
            ),
        ) {}
    }
}

private fun checkRoleHeld(context: Context): Boolean {
    val roleManager = (context.getSystemService(Context.ROLE_SERVICE) as? RoleManager) ?: return false
    return roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
}
