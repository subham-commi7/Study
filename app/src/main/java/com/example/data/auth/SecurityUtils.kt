package com.example.data.auth

import java.security.MessageDigest
import java.security.SecureRandom

object SecurityUtils {

    fun generateSalt(): String {
        val random = SecureRandom()
        val saltBytes = ByteArray(16)
        random.nextBytes(saltBytes)
        return bytesToHex(saltBytes)
    }

    fun hashPassword(password: String, salt: String): String {
        val combined = "$salt:$password"
        val md = MessageDigest.getInstance("SHA-256")
        val hashBytes = md.digest(combined.toByteArray(Charsets.UTF_8))
        return bytesToHex(hashBytes)
    }

    fun verifyPassword(password: String, salt: String, expectedHash: String): Boolean {
        val computedHash = hashPassword(password, salt)
        return computedHash.equals(expectedHash, ignoreCase = true)
    }

    fun isValidEmail(email: String): Boolean {
        val trimmed = email.trim()
        if (trimmed.isEmpty()) return false
        val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
        return emailRegex.matches(trimmed)
    }

    fun validatePasswordStrength(password: String): String? {
        if (password.length < 8) {
            return "Password must be at least 8 characters long."
        }
        if (!password.any { it.isUpperCase() }) {
            return "Password must contain at least 1 uppercase letter."
        }
        if (!password.any { it.isLowerCase() }) {
            return "Password must contain at least 1 lowercase letter."
        }
        if (!password.any { it.isDigit() }) {
            return "Password must contain at least 1 number."
        }
        return null
    }

    fun hasMinPasswordLength(password: String): Boolean = password.length >= 8
    fun hasUppercase(password: String): Boolean = password.any { it.isUpperCase() }
    fun hasLowercase(password: String): Boolean = password.any { it.isLowerCase() }
    fun hasDigit(password: String): Boolean = password.any { it.isDigit() }

    fun isValidOtp(otp: String): Boolean {
        val trimmed = otp.trim()
        return trimmed.length == 6 && trimmed.all { it.isDigit() }
    }

    /**
     * Generates a candidate 8-digit numeric student ID (10000000 to 99999999).
     */
    fun generateCandidateStudentId(): String {
        val random = SecureRandom()
        val num = 10000000 + random.nextInt(90000000)
        return num.toString()
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }
}
