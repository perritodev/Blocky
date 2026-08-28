package com.omargarcia.blocky

import android.content.Context
import io.michaelrocks.libphonenumber.android.PhoneNumberUtil
import io.michaelrocks.libphonenumber.android.Phonenumber.PhoneNumber
import java.util.Locale

data class PhoneNumberDetails(
    val rawNumber: String,
    val formattedInternational: String,
    val formattedNational: String,
    val countryCode: Int,
    val regionCode: String,
    val countryName: String,
    val flagEmoji: String,
    val location: String,
    val numberType: String,
    val isValid: Boolean
)

class PhoneNumberMetadataHelper(context: Context) {

    private val phoneUtil: PhoneNumberUtil = PhoneNumberUtil.createInstance(context)

    fun getNumberDetails(rawNumber: String, locale: Locale = Locale.getDefault()): PhoneNumberDetails {
        val trimmed = rawNumber.trim()
        if (trimmed.isBlank() || trimmed.equals("Private / Unknown", ignoreCase = true) || trimmed.equals("Unknown", ignoreCase = true)) {
            return PhoneNumberDetails(
                rawNumber = rawNumber,
                formattedInternational = rawNumber,
                formattedNational = rawNumber,
                countryCode = 0,
                regionCode = "",
                countryName = "",
                flagEmoji = "📵",
                location = "",
                numberType = "Unknown",
                isValid = false
            )
        }

        val defaultRegion = if (locale.country.isNotBlank()) locale.country else "US"

        return try {
            val protoNumber: PhoneNumber = phoneUtil.parse(trimmed, defaultRegion)
            val isValid = phoneUtil.isValidNumber(protoNumber)
            val regionCode = phoneUtil.getRegionCodeForNumber(protoNumber) ?: defaultRegion
            val countryCode = protoNumber.countryCode

            val countryName = if (regionCode.isNotBlank()) {
                try {
                    Locale.Builder().setRegion(regionCode).build().getDisplayCountry(locale).ifBlank { regionCode }
                } catch (_: Exception) {
                    regionCode
                }
            } else {
                ""
            }

            val formattedIntl = phoneUtil.format(protoNumber, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)
            val formattedNatl = phoneUtil.format(protoNumber, PhoneNumberUtil.PhoneNumberFormat.NATIONAL)

            val numberTypeEnum = phoneUtil.getNumberType(protoNumber)
            val numberTypeStr = when (numberTypeEnum) {
                PhoneNumberUtil.PhoneNumberType.FIXED_LINE -> "Fixed Line"
                PhoneNumberUtil.PhoneNumberType.MOBILE -> "Mobile"
                PhoneNumberUtil.PhoneNumberType.FIXED_LINE_OR_MOBILE -> "Fixed / Mobile"
                PhoneNumberUtil.PhoneNumberType.TOLL_FREE -> "Toll Free"
                PhoneNumberUtil.PhoneNumberType.PREMIUM_RATE -> "Premium Rate"
                PhoneNumberUtil.PhoneNumberType.SHARED_COST -> "Shared Cost"
                PhoneNumberUtil.PhoneNumberType.VOIP -> "VoIP"
                PhoneNumberUtil.PhoneNumberType.PERSONAL_NUMBER -> "Personal Number"
                PhoneNumberUtil.PhoneNumberType.PAGER -> "Pager"
                PhoneNumberUtil.PhoneNumberType.UAN -> "Universal Access (UAN)"
                PhoneNumberUtil.PhoneNumberType.VOICEMAIL -> "Voicemail"
                else -> "Standard Phone"
            }

            PhoneNumberDetails(
                rawNumber = rawNumber,
                formattedInternational = formattedIntl,
                formattedNational = formattedNatl,
                countryCode = countryCode,
                regionCode = regionCode,
                countryName = countryName,
                flagEmoji = countryCodeToEmojiFlag(regionCode),
                location = countryName,
                numberType = numberTypeStr,
                isValid = isValid
            )
        } catch (_: Exception) {
            PhoneNumberDetails(
                rawNumber = rawNumber,
                formattedInternational = rawNumber,
                formattedNational = rawNumber,
                countryCode = 0,
                regionCode = "",
                countryName = "",
                flagEmoji = "📞",
                location = "",
                numberType = "Unknown",
                isValid = false
            )
        }
    }

    companion object {
        fun countryCodeToEmojiFlag(countryCode: String?): String {
            if (countryCode.isNullOrBlank() || countryCode.length != 2) return "🌐"
            val upper = countryCode.uppercase(Locale.US)
            val firstChar = Character.codePointAt(upper, 0) - 0x41 + 0x1F1E6
            val secondChar = Character.codePointAt(upper, 1) - 0x41 + 0x1F1E6
            return try {
                val chars1 = Character.toChars(firstChar)
                val chars2 = Character.toChars(secondChar)
                String(chars1) + String(chars2)
            } catch (_: Exception) {
                "🌐"
            }
        }
    }
}
