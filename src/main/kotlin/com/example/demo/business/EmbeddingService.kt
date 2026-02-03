package com.example.demo.business

import com.example.demo.adapter.out.client.EmbeddingApiFactory
import org.slf4j.LoggerFactory
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
    private val log = LoggerFactory.getLogger(EmbeddingService::class.java)
    suspend fun embed(
        request: EmbeddingApiRequest,
        applicationId: String,
    ): List<EmbeddingApiResponse> = coroutineScope {
        request.models.map { modelSpec ->
            async(Dispatchers.IO) {
                val start = Instant.now()
                log.info("embedding call start={}, vendor={}, model={}, texts={}", start, modelSpec.vendor, modelSpec.version, request.texts.size)
                val client = embeddingApiFactory.getClient(modelSpec.vendor)
                val embeddings = client.embed(
                    texts = request.texts,
                    applicationId = applicationId,
                    model = modelSpec.version,
                    vendorOptions = modelSpec.vendorOptions
                )
                val dimensions = embeddings.firstOrNull()?.size
                val took = Duration.between(start, Instant.now())
                log.info("embedding call end={} took={}ms ({}s) vendor={} model={} applicationId={} dimensions={}", start, took.toMillis(), "%.3f".format(took.toMillis() / 1000.0), modelSpec.vendor, modelSpec.version, applicationId, dimensions)
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
