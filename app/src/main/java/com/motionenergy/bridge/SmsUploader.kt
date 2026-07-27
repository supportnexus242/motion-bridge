package com.motionenergy.bridge

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class SmsUploader(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun uploadSms(message: String): Boolean {
        val prefs = context.getSharedPreferences("motion_bridge", Context.MODE_PRIVATE)
        
        val url = prefs.getString("supabase_url", "") ?: return false
        val deviceId = prefs.getString("device_id", "") ?: return false
        val secret = prefs.getString("secret", "") ?: return false
        val network = prefs.getString("network", "MTN") ?: "MTN"

        if (url.isEmpty() || deviceId.isEmpty() || secret.isEmpty()) return false

        val webhookUrl = "$url/functions/v1/sms-webhook"

        val body = JSONObject().apply {
            put("message", message)
            put("network", network)
            put("device_id", deviceId)
            put("secret", secret)
            put("timestamp", System.currentTimeMillis())
        }

        return try {
            val request = Request.Builder()
                .url(webhookUrl)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val success = response.isSuccessful
                
                if (success) {
                    val editor = prefs.edit()
                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    val lastResetDate = prefs.getString("last_reset_date", "")
                    
                    if (lastResetDate != today) {
                        editor.putInt("sms_count_today", 0)
                        editor.putFloat("total_amount_today", 0f)
                        editor.putString("last_reset_date", today)
                    }
                    
                    editor.putInt("sms_count_today", prefs.getInt("sms_count_today", 0) + 1)
                    
                    val amountPattern = Regex("GHS?\\s*([0-9,]+\\.?[0-9]*)", RegexOption.IGNORE_CASE)
                    amountPattern.find(message)?.let { match ->
                        try {
                            val amount = match.groupValues[1].replace(",", "").toFloat()
                            editor.putFloat("total_amount_today", 
                                prefs.getFloat("total_amount_today", 0f) + amount)
                        } catch (e: Exception) {}
                    }
                    
                    val nowStr = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
                    editor.putString("last_sms_time", nowStr)
                    editor.putString("last_server_ping", nowStr)
                    editor.apply()
                }
                
                success
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
