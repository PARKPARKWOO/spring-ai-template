package com.example.demo.adapter.out.client

import com.example.demo.business.exception.AiServiceException
import com.example.demo.dto.ImageSource
import com.example.demo.model.ApiErrorCode
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.URI
import java.util.Base64

/**
 * [ImageSource]를 실제 이미지 바이트 + MIME 타입으로 변환.
 *
 * URL 입력은 SSRF 방지를 위해 허용 호스트 화이트리스트를 적용한다.
 * StorageKey는 내부 MinIO 엔드포인트(`minio-1:9000`)로 직접 조회한다.
 */
@Component
class ImageResolver(
    private val urlFetchRestClient: RestClient,
    @Value("\${vision.allowed-hosts:bucket.platformholder.site}")
    allowedHostsProp: String,
    @Value("\${vision.storage.internal-url:http://minio-1:9000}")
    private val storageInternalUrl: String,
    @Value("\${vision.max-image-bytes:20971520}") // 20 MB
    private val maxImageBytes: Long,
    @Value("\${vision.max-document-bytes:52428800}") // 50 MB (Gemini inlineData 한도 20MB 초과 시 거부)
    private val maxDocumentBytes: Long,
) {
    private val log = LoggerFactory.getLogger(ImageResolver::class.java)
    private val allowedHosts: Set<String> =
        allowedHostsProp.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet()

    companion object {
        private const val PDF_MIME = "application/pdf"
        private const val GEMINI_INLINE_LIMIT_BYTES: Long = 20L * 1024 * 1024
    }

    private val allowedMimeTypes: Set<String> = setOf(
        "image/png",
        "image/jpeg",
        "image/jpg",
        "image/webp",
        "image/gif",
        "image/heic",
        "image/heif",
        PDF_MIME,
    )

    data class ResolvedImage(val bytes: ByteArray, val mimeType: String)

    fun resolve(source: ImageSource): ResolvedImage = when (source) {
        is ImageSource.Base64 -> resolveBase64(source)
        is ImageSource.Url -> resolveUrl(source)
        is ImageSource.StorageKey -> resolveStorageKey(source)
    }

    private fun resolveBase64(source: ImageSource.Base64): ResolvedImage {
        ensureAllowedMime(source.mimeType)
        val bytes = try {
            Base64.getDecoder().decode(source.data)
        } catch (e: IllegalArgumentException) {
            throw AiServiceException(
                ApiErrorCode.AI_REQUEST_VALIDATION_FAILED,
                "Base64 디코딩 실패: ${e.message}",
            )
        }
        ensureSize(bytes.size.toLong(), source.mimeType)
        return ResolvedImage(bytes, source.mimeType)
    }

    private fun resolveUrl(source: ImageSource.Url): ResolvedImage {
        val uri = try {
            URI.create(source.url)
        } catch (e: IllegalArgumentException) {
            throw AiServiceException(
                ApiErrorCode.AI_REQUEST_VALIDATION_FAILED,
                "유효하지 않은 이미지 URL: ${source.url}",
            )
        }
        val host = uri.host
        if (host.isNullOrBlank() || !allowedHosts.contains(host)) {
            throw AiServiceException(
                ApiErrorCode.AI_REQUEST_VALIDATION_FAILED,
                "허용되지 않은 이미지 호스트입니다: $host (허용: $allowedHosts)",
            )
        }
        return fetchBinary(uri)
    }

    private fun resolveStorageKey(source: ImageSource.StorageKey): ResolvedImage {
        val uri = URI.create("$storageInternalUrl/${source.bucket}/${source.key}")
        return fetchBinary(uri)
    }

    private fun fetchBinary(uri: URI): ResolvedImage {
        val response = try {
            urlFetchRestClient.get()
                .uri(uri)
                .retrieve()
                .toEntity(ByteArray::class.java)
        } catch (e: Exception) {
            log.warn("미디어 가져오기 실패: uri={} err={}", uri, e.message)
            throw AiServiceException(
                ApiErrorCode.AI_NETWORK_ERROR,
                "미디어 가져오기 실패: ${e.message}",
            )
        }
        val bytes = response.body ?: throw AiServiceException(
            ApiErrorCode.AI_NETWORK_ERROR,
            "미디어 응답 본문이 비어있습니다",
        )
        val contentType = response.headers.contentType?.toString() ?: "image/jpeg"
        val normalized = contentType.substringBefore(';').trim()
        ensureAllowedMime(normalized)
        ensureSize(bytes.size.toLong(), normalized)
        return ResolvedImage(bytes, normalized)
    }

    private fun ensureAllowedMime(mimeType: String) {
        if (mimeType !in allowedMimeTypes) {
            throw AiServiceException(
                ApiErrorCode.AI_REQUEST_VALIDATION_FAILED,
                "허용되지 않은 MIME 타입입니다: $mimeType (허용: $allowedMimeTypes)",
            )
        }
    }

    private fun ensureSize(size: Long, mimeType: String) {
        if (size <= 0) {
            throw AiServiceException(
                ApiErrorCode.AI_REQUEST_VALIDATION_FAILED,
                "미디어 바이트가 비어있습니다",
            )
        }
        val limit = if (mimeType == PDF_MIME) maxDocumentBytes else maxImageBytes
        if (size > limit) {
            throw AiServiceException(
                ApiErrorCode.AI_REQUEST_VALIDATION_FAILED,
                "미디어 크기 한도를 초과했습니다: ${size}B > ${limit}B (mimeType=$mimeType)",
            )
        }
        // PDF 는 Gemini inlineData 한도가 20MB. 그 이상은 향후 File API 분기 대상이지만 현재는 거부.
        if (mimeType == PDF_MIME && size > GEMINI_INLINE_LIMIT_BYTES) {
            throw AiServiceException(
                ApiErrorCode.AI_REQUEST_VALIDATION_FAILED,
                "Gemini inlineData PDF 한도(20MB)를 초과했습니다: ${size}B. 현재 File API 분기 미지원.",
            )
        }
    }
}
