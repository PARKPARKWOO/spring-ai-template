package com.example.demo.common.crypto

import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * API 키 저장용 양방향 암호화 (AES-256-GCM).
 * DB에 저장할 때 암호화, 조회 시 복호화합니다.
 */
@Service
class ApiKeyCryptoService(
    private val secretBase64: String,
) {
    private val aesKey: SecretKeySpec by lazy {
        val keyBytes = Base64.getDecoder().decode(secretBase64)
        require(keyBytes.size == 32) { "API 키 암호화용 secret은 Base64 인코딩된 32바이트여야 합니다." }
        SecretKeySpec(keyBytes, "AES")
    }

    private val random = SecureRandom()

    companion object {
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
        private const val PREFIX = "enc:"
    }

    /**
     * 평문을 암호화해 DB 저장용 문자열로 반환 (prefix "enc:" + base64(iv||ciphertext)).
     */
    fun encrypt(plainText: String): String {
        val iv = ByteArray(GCM_IV_LENGTH).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val ciphertext = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        val combined = iv + ciphertext
        return PREFIX + Base64.getEncoder().encodeToString(combined)
    }

    /**
     * DB에서 읽은 값을 복호화. "enc:" 접두사가 있으면 복호화, 없으면 기존 평문으로 간주해 그대로 반환.
     */
    fun decrypt(stored: String): String {
        if (!stored.startsWith(PREFIX)) {
            return stored
        }
        val payload = stored.removePrefix(PREFIX)
        val combined = Base64.getDecoder().decode(payload)
        require(combined.size >= GCM_IV_LENGTH) { "저장된 암호문이 올바르지 않습니다." }
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }
}
