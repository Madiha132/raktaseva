// SmsHelper.kt
package com.example.raktaseva

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object SmsHelper {

    const val SMS_PERMISSION_REQUEST_CODE = 1001

    // ── Check & request SMS permission at runtime ──────────────
    fun hasSmsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun requestSmsPermission(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.SEND_SMS),
            SMS_PERMISSION_REQUEST_CODE
        )
    }

    // ── PRIMARY: Android native SMS ────────────────────────────
    fun sendViaNative(context: Context, phone: String, message: String): Boolean {
        return try {
            val normalised = normalisePhone(phone)
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                context.getSystemService(SmsManager::class.java)
            else @Suppress("DEPRECATION") SmsManager.getDefault()

            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(normalised, null, parts, null, null)
            Log.d("SMS_NATIVE", "✅ Sent to $normalised")
            true
        } catch (e: Exception) {
            Log.e("SMS_NATIVE", "❌ Failed: ${e.message}")
            false
        }
    }

    // ── FALLBACK: Fast2SMS API (free tier — India numbers) ─────
    // Sign up free at https://www.fast2sms.com → get API key
    fun sendViaFast2Sms(
        phone: String,
        message: String,
        apiKey: String,          // your Fast2SMS API key
        onResult: (Boolean) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL(
                    "https://www.fast2sms.com/dev/bulkV2" +
                            "?authorization=${URLEncoder.encode(apiKey, "UTF-8")}" +
                            "&route=q" +           // quick transactional route
                            "&message=${URLEncoder.encode(message, "UTF-8")}" +
                            "&language=english" +
                            "&flash=0" +
                            "&numbers=${phone.takeLast(10)}"   // 10-digit only
                )
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 10_000
                conn.readTimeout    = 10_000
                val code = conn.responseCode
                val body = conn.inputStream.bufferedReader().readText()
                Log.d("SMS_F2S", "Response $code : $body")
                onResult(code == 200)
            } catch (e: Exception) {
                Log.e("SMS_F2S", "Error: ${e.message}")
                onResult(false)
            }
        }
    }

    // ── FALLBACK 2: Twilio REST API (global, reliable) ─────────
    // Sign up at https://www.twilio.com → free trial gives ~$15 credit
    fun sendViaTwilio(
        toPhone: String,
        message: String,
        accountSid: String,      // from Twilio dashboard
        authToken: String,       // from Twilio dashboard
        fromNumber: String,      // your Twilio number e.g. "+15551234567"
        onResult: (Boolean) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val normalised = normalisePhone(toPhone)
                val url = URL("https://api.twilio.com/2010-04-01/Accounts/$accountSid/Messages.json")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 10_000

                // Basic auth
                val creds = android.util.Base64.encodeToString(
                    "$accountSid:$authToken".toByteArray(), android.util.Base64.NO_WRAP
                )
                conn.setRequestProperty("Authorization", "Basic $creds")
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

                val body = "To=${URLEncoder.encode(normalised, "UTF-8")}" +
                        "&From=${URLEncoder.encode(fromNumber, "UTF-8")}" +
                        "&Body=${URLEncoder.encode(message, "UTF-8")}"
                conn.outputStream.write(body.toByteArray())

                val code = conn.responseCode
                Log.d("SMS_TWILIO", "Response: $code")
                onResult(code in 200..201)
            } catch (e: Exception) {
                Log.e("SMS_TWILIO", "Error: ${e.message}")
                onResult(false)
            }
        }
    }

    // ── Normalise Indian phone numbers ─────────────────────────
    fun normalisePhone(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        return when {
            phone.startsWith("+")  -> phone
            digits.startsWith("91") && digits.length == 12 -> "+$digits"
            digits.startsWith("0")  && digits.length == 11 -> "+91${digits.substring(1)}"
            digits.length == 10     -> "+91$digits"
            else -> "+$digits"
        }
    }
}