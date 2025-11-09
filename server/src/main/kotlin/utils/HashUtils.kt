package dev.ixor.utils

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.SecureRandom
import java.util.*

object HashUtils {
    private val secureRandom = SecureRandom()
    
    fun hashPassword(password: String): String {
        val salt = ByteArray(16)
        secureRandom.nextBytes(salt)
        
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(2)
            .withMemoryAsKB(65536)
            .withParallelism(1)
            .withSalt(salt)
            .build()
        
        val generator = Argon2BytesGenerator()
        generator.init(params)
        
        val hash = ByteArray(32)
        generator.generateBytes(password.toByteArray(), hash)
        
        return Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(hash)
    }
    
    fun verifyPassword(password: String, hashedPassword: String): Boolean {
        val parts = hashedPassword.split(":")
        if (parts.size != 2) return false
        
        val salt = Base64.getDecoder().decode(parts[0])
        val expectedHash = Base64.getDecoder().decode(parts[1])
        
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(2)
            .withMemoryAsKB(65536)
            .withParallelism(1)
            .withSalt(salt)
            .build()
        
        val generator = Argon2BytesGenerator()
        generator.init(params)
        
        val actualHash = ByteArray(32)
        generator.generateBytes(password.toByteArray(), actualHash)
        
        return expectedHash.contentEquals(actualHash)
    }
    
    fun generateToken(username: String, passwordHash: String, machineId: String): String {
        val combined = "$username:$passwordHash:$machineId"
        return Base64.getEncoder().encodeToString(combined.toByteArray()).replace("=", "")
    }
    
    fun generateSessionToken(): String {
        val randomBytes = ByteArray(32)
        secureRandom.nextBytes(randomBytes)
        return Base64.getEncoder().encodeToString(randomBytes).replace("=", "")
    }
}