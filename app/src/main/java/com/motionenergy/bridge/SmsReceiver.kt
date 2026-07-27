package com.motionenergy.bridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.*

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val prefs = context.getSharedPreferences("motion_bridge", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("active", true)) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return

        val fullMessage = messages.joinToString("") { it.messageBody ?: "" }
        val sender = messages[0].originatingAddress ?: ""

        val trustedSenders = listOf("MobileMoney", "MTN", "Telecel", "MoMo", "MTNMoMo", 
            "Vodafone", "AirtelTigo", "MTN Ghana", "TeleCash", "Voda-Cash")
        
        val isRelevant = trustedSenders.any { sender.contains(it, ignoreCase = true) } ||
                fullMessage.contains("GHS", ignoreCase = true) ||
                fullMessage.contains("received", ignoreCase = true) ||
                fullMessage.contains("payment", ignoreCase = true)

        if (!isRelevant) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                SmsUploader(context).uploadSms(fullMessage)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
