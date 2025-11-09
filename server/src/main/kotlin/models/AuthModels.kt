package dev.ixor.models

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val machineId: String,
    val authType: String, // "PASSWD" or "TOKEN"
    val username: String,
    val password: String? = null, // Required when authType is "PASSWD"
    val clientToken: String? = null // Required when authType is "TOKEN"
)

@Serializable
data class LoginResponse(
    val success: Boolean,
    val sessionToken: String? = null,
    val clientToken: String? = null,
    val message: String? = null
)

@Serializable
data class WebSocketAuthMessage(
    val type: String = "AUTH",
    val sessionToken: String
)

@Serializable
data class RegisterRequest(
    val username: String,
    val password: String
)

@Serializable
data class RegisterResponse(
    val success: Boolean,
    val message: String? = null
)