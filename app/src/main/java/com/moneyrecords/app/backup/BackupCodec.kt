package com.moneyrecords.app.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Why a file couldn't be read as a backup. [message] is shown to the user. */
sealed class BackupError(message: String) : Exception(message) {
    class NotABackup : BackupError("This file isn't a MoneyRecords backup.")
    class NewerVersion : BackupError("This backup was made by a newer version of MoneyRecords. Update the app to restore it.")
    class PasswordNeeded : BackupError("This backup is protected with a password.")
    class WrongPassword : BackupError("That password doesn't open this backup.")
}

/**
 * A password-protected backup: the plain backup JSON encrypted with AES-256-GCM under a key derived
 * from the password (PBKDF2-HMAC-SHA256). GCM also detects any change to the file.
 */
@Serializable
data class EncryptedBackup(
    val format: String = BACKUP_FORMAT,
    val version: Int = FORMAT_VERSION,
    val encrypted: Boolean = true,
    val kdf: String = "PBKDF2WithHmacSHA256",
    val iterations: Int,
    val salt: String,
    val iv: String,
    val payload: String,
)

object BackupCodec {
    private val json = Json {
        ignoreUnknownKeys = true // newer files with extra fields still open
        encodeDefaults = true
        prettyPrint = false
    }
    private const val ITERATIONS = 120_000
    private val random = SecureRandom()

    fun encode(file: BackupFile, password: String? = null): ByteArray {
        val plain = json.encodeToString(BackupFile.serializer(), file).toByteArray(Charsets.UTF_8)
        if (password.isNullOrEmpty()) return plain
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt, ITERATIONS), GCMParameterSpec(128, iv))
        val envelope = EncryptedBackup(
            iterations = ITERATIONS,
            salt = b64(salt),
            iv = b64(iv),
            payload = b64(cipher.doFinal(plain)),
        )
        return json.encodeToString(EncryptedBackup.serializer(), envelope).toByteArray(Charsets.UTF_8)
    }

    /** True when [bytes] is a password-protected backup (so the UI can ask for the password). */
    fun isEncrypted(bytes: ByteArray): Boolean = header(bytes)?.let { it["encrypted"]?.jsonPrimitive?.booleanOrNull } == true

    /** Reads a backup, decrypting it with [password] when needed. Throws [BackupError]. */
    fun decode(bytes: ByteArray, password: String? = null): BackupFile {
        val head = header(bytes) ?: throw BackupError.NotABackup()
        if (head["format"]?.jsonPrimitive?.contentOrNull != BACKUP_FORMAT) throw BackupError.NotABackup()
        val version = head["version"]?.jsonPrimitive?.intOrNull ?: throw BackupError.NotABackup()
        if (version > FORMAT_VERSION) throw BackupError.NewerVersion()

        val plain = if (head["encrypted"]?.jsonPrimitive?.booleanOrNull == true) {
            if (password.isNullOrEmpty()) throw BackupError.PasswordNeeded()
            val envelope = try {
                json.decodeFromString(EncryptedBackup.serializer(), bytes.toString(Charsets.UTF_8))
            } catch (e: SerializationException) {
                throw BackupError.NotABackup()
            }
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    key(password, unb64(envelope.salt), envelope.iterations),
                    GCMParameterSpec(128, unb64(envelope.iv)),
                )
                cipher.doFinal(unb64(envelope.payload))
            } catch (e: AEADBadTagException) {
                throw BackupError.WrongPassword()
            } catch (e: IllegalArgumentException) {
                throw BackupError.NotABackup()
            }
        } else bytes

        return try {
            json.decodeFromString(BackupFile.serializer(), plain.toString(Charsets.UTF_8))
        } catch (e: SerializationException) {
            throw BackupError.NotABackup()
        } catch (e: IllegalArgumentException) {
            throw BackupError.NotABackup()
        }
    }

    private fun header(bytes: ByteArray): JsonObject? = try {
        json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
    } catch (e: Exception) {
        null
    }

    private fun key(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }

    private fun b64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
    private fun unb64(text: String) = Base64.getDecoder().decode(text)
}
