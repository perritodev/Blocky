package com.omargarcia.blocky

import android.Manifest
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.MediaPlayer
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telephony.PhoneNumberUtils
import androidx.core.content.ContextCompat
import com.omargarcia.blocky.data.AppDatabase
import com.omargarcia.blocky.data.BlockedCall
import com.omargarcia.blocky.data.BlockedCallDao
import com.omargarcia.blocky.data.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CallBlockerService : CallScreeningService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onScreenCall(callDetails: Call.Details) {
        val settingsManager = SettingsManager(this)
        
        if (!settingsManager.isBlockingEnabled) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        val rawNumber = callDetails.handle?.schemeSpecificPart ?: ""
        val normalizedNumber = PhoneNumberUtils.normalizeNumber(rawNumber)

        serviceScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val whitelistDao = db.whitelistedNumberDao()
            val permanentBlockDao = db.permanentBlockedNumberDao()
            val unblockedDao = db.unblockedNumberDao()
            val historyDao = db.blockedCallDao()

            // Handle Private / Hidden / Anonymous numbers
            if (rawNumber.isBlank()) {
                blockCall(callDetails, "Private / Unknown", historyDao, settingsManager)
                return@launch
            }

            // 1. Check if number is explicitly in the permanent blocked list
            val blockedList = permanentBlockDao.getAllList().map { it.phoneNumber }
            if (isNumberInList(rawNumber, normalizedNumber, blockedList)) {
                blockCall(callDetails, rawNumber, historyDao, settingsManager)
                return@launch
            }

            // 2. Check if number was unblocked by the user
            val unblockedList = unblockedDao.getAllList().map { it.phoneNumber }
            if (isNumberInList(rawNumber, normalizedNumber, unblockedList)) {
                allowCall(callDetails)
                return@launch
            }

            // 3. Check if number is whitelisted
            val whitelist = whitelistDao.getAllList().map { it.phoneNumber }
            if (isNumberInList(rawNumber, normalizedNumber, whitelist)) {
                allowCall(callDetails)
                return@launch
            }

            // 4. Check if number is in user's Contacts (with strict verification)
            val inContacts = isNumberInContacts(rawNumber) || 
                            (normalizedNumber.isNotBlank() && isNumberInContacts(normalizedNumber))
            if (inContacts) {
                allowCall(callDetails)
                return@launch
            }

            // 5. Check repeat caller threshold (if configured > 1)
            val threshold = settingsManager.repeatCallThreshold
            if (threshold > 1) {
                val intervalMinutes = settingsManager.repeatCallIntervalMinutes
                val windowStartTime = System.currentTimeMillis() - (intervalMinutes * 60 * 1000L)
                val recentBlockedCalls = historyDao.getBlockedCallsSinceList(windowStartTime)
                val recentMatchingCount = countMatchingCalls(rawNumber, normalizedNumber, recentBlockedCalls)
                val totalAttempts = recentMatchingCount + 1
                if (totalAttempts >= threshold) {
                    allowCall(callDetails)
                    return@launch
                }
            }

            // 6. Unknown caller not in contacts or whitelist -> Block
            blockCall(callDetails, rawNumber, historyDao, settingsManager)
        }
    }

    private fun allowCall(callDetails: Call.Details) {
        respondToCall(callDetails, CallResponse.Builder().build())
    }

    private suspend fun blockCall(
        callDetails: Call.Details, 
        displayLogNumber: String, 
        historyDao: BlockedCallDao,
        settingsManager: SettingsManager
    ) {
        val response = CallResponse.Builder()
            .setDisallowCall(true)
            .setRejectCall(true)
            .setSkipCallLog(false)
            .setSkipNotification(true)
            .build()

        historyDao.insert(BlockedCall(phoneNumber = displayLogNumber))
        playBlockedSound(settingsManager)
        respondToCall(callDetails, response)
    }

    private fun playBlockedSound(settingsManager: SettingsManager) {
        if (!settingsManager.isBlockSoundEnabled) return
        try {
            val player = MediaPlayer.create(applicationContext, R.raw.sfx_hit)
            player?.apply {
                setVolume(0.85f, 0.85f)
                setOnCompletionListener { mp ->
                    try {
                        mp.stop()
                        mp.release()
                    } catch (_: Exception) {}
                }
                setOnErrorListener { mp, _, _ ->
                    try {
                        mp.release()
                    } catch (_: Exception) {}
                    true
                }
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Suppress("DEPRECATION")
    private fun isNumberInList(rawNumber: String, normalizedNumber: String, list: List<String>): Boolean {
        for (item in list) {
            val normalizedItem = PhoneNumberUtils.normalizeNumber(item)
            if (item == rawNumber || 
                (normalizedNumber.isNotEmpty() && item == normalizedNumber) ||
                (normalizedItem.isNotEmpty() && normalizedItem == normalizedNumber) ||
                PhoneNumberUtils.compare(this, rawNumber, item) ||
                (normalizedNumber.isNotEmpty() && PhoneNumberUtils.compare(this, normalizedNumber, item))
            ) {
                return true
            }
        }
        return false
    }

    @Suppress("DEPRECATION")
    private fun isNumberInContacts(number: String): Boolean {
        if (number.isBlank()) return false
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return false
        }
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(number)
        )
        val projection = arrayOf(
            ContactsContract.PhoneLookup._ID,
            ContactsContract.PhoneLookup.NUMBER
        )
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(uri, projection, null, null, null)
            if (cursor != null) {
                val numberColumnIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.NUMBER)
                while (cursor.moveToNext()) {
                    if (numberColumnIndex != -1) {
                        val contactNumber = cursor.getString(numberColumnIndex)
                        if (!contactNumber.isNullOrBlank() && PhoneNumberUtils.compare(this, number, contactNumber)) {
                            return true
                        }
                    } else {
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }
        return false
    }

    @Suppress("DEPRECATION")
    private fun countMatchingCalls(rawNumber: String, normalizedNumber: String, calls: List<BlockedCall>): Int {
        var count = 0
        for (call in calls) {
            val item = call.phoneNumber
            val normalizedItem = PhoneNumberUtils.normalizeNumber(item)
            if (item == rawNumber || 
                (normalizedNumber.isNotEmpty() && item == normalizedNumber) ||
                (normalizedItem.isNotEmpty() && normalizedItem == normalizedNumber) ||
                PhoneNumberUtils.compare(this, rawNumber, item) ||
                (normalizedNumber.isNotEmpty() && PhoneNumberUtils.compare(this, normalizedNumber, item))
            ) {
                count++
            }
        }
        return count
    }
}

