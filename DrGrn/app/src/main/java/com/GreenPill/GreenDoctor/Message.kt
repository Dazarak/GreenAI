package com.GreenPill.GreenDoctor

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class Message(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val imageBase64: String? = null,
    val timestamp: String = getCurrentTimestamp(),
    val isUser: Boolean // true = Utilisateur, false = IA
) {
    companion object {
        fun getCurrentTimestamp(): String {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            return sdf.format(Date())
        }
    }
}