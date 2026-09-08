package com.GreenPill.GreenDoctor

data class Message(
    val id: String = java.util.UUID.randomUUID().toString(),
    val content: String,
    val timestamp: String,
    val isUser: Boolean // true = utilisateur (droite), false = IA (gauche)
)