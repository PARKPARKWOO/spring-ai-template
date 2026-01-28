package com.example.demo.business

import com.example.demo.adapter.out.client.EmbeddingApiFactory
import com.example.demo.common.logger
import com.example.demo.dto.EmbeddingApiRequest
import com.example.demo.dto.EmbeddingApiResponse
import com.example.demo.dto.EmbeddingModelSpec
import com.example.demo.dto.EmbeddingVendorOptions
import com.example.demo.model.Vendor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

@Service
class EmbeddingService(
    private val embeddingApiFactory: EmbeddingApiFactory,
) {
    suspend fun embed(
        request: EmbeddingApiRequest,
        clientId: Long,
    ): List<EmbeddingApiResponse> = coroutineScope {
        request.models.map { modelSpec ->
            async(Dispatchers.IO) {
                val start = Instant.now()
                logger().info("embedding call start={}, vendor={}, model={}, texts={}", start, modelSpec.vendor, modelSpec.version, request.texts.size)

                val client = embeddingApiFactory.getClient(modelSpec.vendor)

                // 벤더별 옵션 전달
                val embeddings = client.embed(
                    texts = request.texts,
                    clientId = clientId,
                    model = modelSpec.version,
                    vendorOptions = modelSpec.vendorOptions
                )

                // 첫 번째 임베딩의 차원 수 확인
                val dimensions = embeddings.firstOrNull()?.size

                val end = Instant.now()
                val took = Duration.between(start, end)
                logger().info(
                    "embedding call end={} took={}ms ({}s) vendor={} model={} clientId={} dimensions={}",
                    end, took.toMillis(), "%.3f".format(took.toMillis() / 1000.0), modelSpec.vendor, modelSpec.version, clientId, dimensions
                )

                EmbeddingApiResponse(
                    vendor = modelSpec.vendor,
                    embeddings = embeddings,
                    dimensions = dimensions,
                    model = modelSpec.version,
                )
            }
        }.awaitAll()
    }
}
