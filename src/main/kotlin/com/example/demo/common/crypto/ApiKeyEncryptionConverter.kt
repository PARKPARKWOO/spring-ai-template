package com.example.demo.common.crypto

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/**
 * JPA AttributeConverter: API 키 필드 저장 시 암호화, 조회 시 복호화.
 * [ApiKeyEncryptionHolder]에 설정된 [ApiKeyCryptoService]를 사용합니다.
 */
@Converter
class ApiKeyEncryptionConverter : AttributeConverter<String, String> {

    override fun convertToDatabaseColumn(attribute: String?): String? {
        if (attribute.isNullOrBlank()) return attribute
        return ApiKeyEncryptionHolder.getCrypto()?.encrypt(attribute) ?: attribute
    }

    override fun convertToEntityAttribute(dbData: String?): String? {
        if (dbData.isNullOrBlank()) return dbData
        return ApiKeyEncryptionHolder.getCrypto()?.decrypt(dbData) ?: dbData
    }
}

/**
 * JPA Converter는 persistence provider가 인스턴스화하므로
 * Spring Bean을 직접 주입할 수 없어, 설정 시점에 암호화 서비스를 등록합니다.
 */
object ApiKeyEncryptionHolder {
    @Volatile
    private var crypto: ApiKeyCryptoService? = null

    fun setCrypto(service: ApiKeyCryptoService) {
        crypto = service
    }

    fun getCrypto(): ApiKeyCryptoService? = crypto
}
