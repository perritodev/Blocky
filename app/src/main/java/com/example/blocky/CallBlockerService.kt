package com.example.blocky

import android.telecom.Call
import android.telecom.CallScreeningService
import com.example.blocky.data.AppDatabase
import com.example.blocky.data.BlockedCall
import com.example.blocky.data.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class CallBlockerService : CallScreeningService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var settingsManager: SettingsManager

    override fun onCreate() {
        super.onCreate()
        settingsManager = SettingsManager(applicationContext)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onScreenCall(callDetails: Call.Details) {
        // If the user turned off the service, allow all calls
        if (!settingsManager.isBlockingEnabled) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        if (callDetails.callDirection == Call.Details.DIRECTION_INCOMING) {
            val phoneNumber = callDetails.handle?.schemeSpecificPart ?: getString(R.string.unknown_number)

            // Log the blocked call to database
            serviceScope.launch {
                val db = AppDatabase.getDatabase(applicationContext)
                db.blockedCallDao().insert(BlockedCall(phoneNumber = phoneNumber))
            }

            // Logic to block the call
            val response = CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipCallLog(false)
                .setSkipNotification(false)
                .build()

            respondToCall(callDetails, response)
        } else {
            val response = CallResponse.Builder().build()
            respondToCall(callDetails, response)
        }
    }
}
