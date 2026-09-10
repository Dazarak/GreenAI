package com.GreenPill.GreenDoctor

data class MessagePair(
    val userContent: String,
    val userImageBase64: String? = null,
    var aiContent: String = "L'IA réfléchit..."
)