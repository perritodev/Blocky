package com.example.blocky

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.blocky.data.PermanentBlockedNumber
import com.example.blocky.data.WhitelistedNumber
import com.example.blocky.ui.theme.BlockyTheme

@Preview(showBackground = true, name = "Onboarding Screen")
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

@Preview(showBackground = true, name = "Protection Screen - Active")
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
            blockedCount = 42
        )
    }
}

@Preview(showBackground = true, name = "Protection Screen - Inactive")
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

@Preview(showBackground = true, name = "Blocked List")
@Composable
fun BlockedListPreview() {
    val mockBlocked = listOf(
        PermanentBlockedNumber(id = 1, phoneNumber = "+1 555-0101"),
        PermanentBlockedNumber(id = 2, phoneNumber = "+1 555-0102"),
        PermanentBlockedNumber(id = 3, phoneNumber = "Unknown")
    )
    BlockyTheme {
        BlockedListScreen(
            blockedList = mockBlocked,
            onUnblock = {},
            onUnblockAll = {},
            onWhitelist = {},
            onAddManual = {}
        )
    }
}

@Preview(showBackground = true, name = "Whitelist Screen")
@Composable
fun WhitelistPreview() {
    val mockWhitelist = listOf(
        WhitelistedNumber(id = 1, phoneNumber = "+1 555-9999"),
        WhitelistedNumber(id = 2, phoneNumber = "Mom")
    )
    BlockyTheme {
        WhitelistScreen(
            whitelist = mockWhitelist,
            onRemove = {},
            onAddManual = {}
        )
    }
}

@Preview(showBackground = true, name = "Troubleshooting / Support Screen")
@Composable
fun TroubleshootingPreview() {
    BlockyTheme {
        TroubleshootingScreen()
    }
}

