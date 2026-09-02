package com.omargarcia.blocky

import android.app.PendingIntent
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.omargarcia.blocky.data.SettingsManager

class BlockyTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val settingsManager = SettingsManager(this)
        val roleHeld = isRoleHeld()

        if (!roleHeld) {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("StartActivityAndCollapseDeprecated", "DEPRECATION")
                startActivityAndCollapse(intent)
            }
            return
        }

        val newState = !settingsManager.isBlockingEnabled
        settingsManager.isBlockingEnabled = newState

        // Manage StatusIndicatorService
        val statusIntent = Intent(this, StatusIndicatorService::class.java)
        if (newState) {
            try {
                startForegroundService(statusIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            try {
                stopService(statusIntent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val settingsManager = SettingsManager(this)
        val roleHeld = isRoleHeld()
        val isProtectionActive = roleHeld && settingsManager.isBlockingEnabled

        tile.label = getString(R.string.tile_label)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_control_center_tile)

        if (isProtectionActive) {
            tile.state = Tile.STATE_ACTIVE
            tile.subtitle = getString(R.string.tile_active)
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.subtitle = if (!roleHeld) getString(R.string.shield_down) else getString(R.string.tile_inactive)
        }

        tile.updateTile()
    }

    private fun isRoleHeld(): Boolean {
        val roleManager = getSystemService(Context.ROLE_SERVICE) as? RoleManager ?: return false
        return try {
            roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        } catch (_: Exception) {
            false
        }
    }
}
