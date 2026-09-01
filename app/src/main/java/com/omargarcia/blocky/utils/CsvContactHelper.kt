package com.omargarcia.blocky.utils

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.omargarcia.blocky.data.BlockedCall
import com.omargarcia.blocky.data.WhitelistedNumber
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

enum class CsvExportOption {
    ALL,
    BLOCKED_ONLY,
    WHITELIST_ONLY
}

object CsvContactHelper {

    /**
     * Exports Blocked calls and Whitelisted numbers to a cached file and opens the Android Share Sheet.
     */
    fun exportAndShareCsv(
        context: Context,
        filename: String,
        blockedList: List<BlockedCall>,
        whitelist: List<WhitelistedNumber>,
        exportOption: CsvExportOption = CsvExportOption.ALL,
        chooserTitle: String = "Share CSV"
    ): Int {
        val exportDir = File(context.cacheDir, "csv_exports")
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }
        val file = File(exportDir, filename)
        var count = 0
        FileOutputStream(file).use { fos ->
            count = exportToGoogleCsv(fos, blockedList, whitelist, exportOption)
        }

        val contentUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/*"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
            clipData = ClipData.newRawUri(file.name, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooserIntent = Intent.createChooser(sendIntent, chooserTitle).apply {
            clipData = ClipData.newRawUri(file.name, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooserIntent)
        return count
    }

    /**
     * Exports Blocked calls and Whitelisted numbers to a Google Contacts compatible CSV format.
     */
    fun exportToGoogleCsv(
        outputStream: OutputStream,
        blockedList: List<BlockedCall>,
        whitelist: List<WhitelistedNumber>,
        exportOption: CsvExportOption = CsvExportOption.ALL
    ): Int {
        var count = 0
        OutputStreamWriter(outputStream, StandardCharsets.UTF_8).use { writer ->
            // Standard Google Contacts CSV header row
            writer.write("Name,Given Name,Family Name,Group Membership,Phone 1 - Type,Phone 1 - Value\r\n")

            // Write Blocked Numbers
            if (exportOption == CsvExportOption.ALL || exportOption == CsvExportOption.BLOCKED_ONLY) {
                for (item in blockedList) {
                    val cleanNum = escapeCsv(item.phoneNumber)
                    val name = escapeCsv("Blocked - ${item.phoneNumber}")
                    val group = escapeCsv("* Blocky Blocked")
                    writer.write("$name,$name,,$group,Mobile,$cleanNum\r\n")
                    count++
                }
            }

            // Write Whitelisted Numbers
            if (exportOption == CsvExportOption.ALL || exportOption == CsvExportOption.WHITELIST_ONLY) {
                for (item in whitelist) {
                    val cleanNum = escapeCsv(item.phoneNumber)
                    val name = escapeCsv("Whitelisted - ${item.phoneNumber}")
                    val group = escapeCsv("* Blocky Whitelist")
                    writer.write("$name,$name,,$group,Mobile,$cleanNum\r\n")
                    count++
                }
            }
            writer.flush()
        }
        return count
    }

    /**
     * Parses an input CSV stream (Google Contacts format or standard phone CSV)
     * and extracts all unique, valid phone numbers.
     */
    fun parseCsvForPhoneNumbers(inputStream: InputStream): List<String> {
        val uniqueNumbers = mutableSetOf<String>()
        BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { reader ->
            val headerLine = reader.readLine() ?: return emptyList()
            val headers = parseCsvRow(headerLine).map { it.trim().lowercase() }

            // Find indexes of phone-related columns
            val phoneColumnIndices = mutableListOf<Int>()
            headers.forEachIndexed { index, header ->
                if (isPhoneHeader(header)) {
                    phoneColumnIndices.add(index)
                }
            }

            var line = reader.readLine()
            while (line != null) {
                if (line.isNotBlank()) {
                    val tokens = parseCsvRow(line)
                    if (phoneColumnIndices.isNotEmpty()) {
                        for (index in phoneColumnIndices) {
                            if (index < tokens.size) {
                                val value = sanitizePhoneNumber(tokens[index])
                                if (value.isNotBlank() && isPossiblePhoneNumber(value)) {
                                    uniqueNumbers.add(value)
                                }
                            }
                        }
                    } else {
                        // Fallback if no known headers matched: inspect every column for valid numbers
                        for (cell in tokens) {
                            val value = sanitizePhoneNumber(cell)
                            if (value.isNotBlank() && isPossiblePhoneNumber(value)) {
                                uniqueNumbers.add(value)
                                break // Usually one phone number per row is expected in fallback
                            }
                        }
                    }
                }
                line = reader.readLine()
            }
        }
        return uniqueNumbers.toList()
    }

    private fun isPhoneHeader(header: String): Boolean {
        return header.contains("phone") || 
               header.contains("mobile") || 
               header.contains("teléfono") || 
               header.contains("telefono") || 
               header.contains("celular") || 
               header.contains("number") || 
               header == "tel"
    }

    private fun sanitizePhoneNumber(raw: String): String {
        return raw.trim().replace("\"", "").replace("'", "")
    }

    private fun isPossiblePhoneNumber(value: String): Boolean {
        // Must contain digits and at least 3 digits
        val digitCount = value.count { it.isDigit() }
        if (digitCount < 3) return false
        // Should only contain valid phone characters
        return value.all { it.isDigit() || it == '+' || it == '-' || it == ' ' || it == '(' || it == ')' || it == '.' || it == '*' || it == '#' }
    }

    private fun escapeCsv(value: String): String {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\""
        }
        return value
    }

    /**
     * Parses a single CSV line according to RFC 4180 rules (supporting quoted values with commas).
     */
    fun parseCsvRow(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = java.lang.StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val c = line[i]
            if (c == '"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    sb.append('"')
                    i++ // skip escaped quote
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == ',' && !inQuotes) {
                result.add(sb.toString().trim())
                sb.setLength(0)
            } else {
                sb.append(c)
            }
            i++
        }
        result.add(sb.toString().trim())
        return result
    }
}
