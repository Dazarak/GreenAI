package com.GreenPill.GreenDoctor

import android.media.Image

data class Message(
    val id: String = java.util.UUID.randomUUID().toString(),
    val image: Image,
    val content: String,
    val timestamp: String,
    val isUser: Boolean // true = utilisateur (droite), false = IA (gauche)
)