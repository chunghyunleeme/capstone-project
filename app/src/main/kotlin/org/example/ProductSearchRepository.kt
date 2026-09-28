package org.example

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.apache.hc.core5.http.HttpHost
import org.example.model.Product
import org.opensearch.client.json.jackson.JacksonJsonpMapper
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch._types.mapping.IntegerNumberProperty
import org.opensearch.client.opensearch._types.mapping.KeywordProperty
import org.opensearch.client.opensearch._types.mapping.Property
import org.opensearch.client.opensearch._types.mapping.TextProperty
import org.opensearch.client.opensearch._types.mapping.TypeMapping
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery
import org.opensearch.client.opensearch._types.query_dsl.Operator
import org.opensearch.client.opensearch._types.query_dsl.Query
import org.opensearch.client.opensearch.indices.ExistsRequest
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder

const val PRODUCTS_INDEX = "products"

interface ProductSearchRepository {
    suspend fun index(product: Product)
    suspend fun search(q: String?, brandId: Int?): List<ProductSearchHit>
}

@Serializable
data class ProductSearchHit(val product: Product, val score: Double?)

class OpenSearchProductSearchRepository(
    private val client: OpenSearchClient
) : ProductSearchRepository {
    override suspend fun index(product: Product) = withContext(Dispatchers.IO) {
        client.index { req ->
            req.index(PRODUCTS_INDEX)
                .id(product.id.toString())
                .document(product)
        }
        Unit
    }

    override suspend fun search(
        q: String?,
        brandId: Int?
    ): List<ProductSearchHit> {
        val mustClause = mutableListOf<Query>()
        val filterClause = mutableListOf<Query>()

        if (!q.isNullOrBlank()) {
            mustClause.add(
                Query.Builder()
                    .match { m -> m.field("name").query { v -> v.stringValue(q)}.operator(Operator.Or)
                    }.build()
            )
        }

        if (brandId != null) {
            filterClause.add(
                Query.Builder().term { t -> t.field("brandId").value { v -> v.longValue(brandId.toLong())}}.build()
            )
        }

        val boolQuery = BoolQuery.Builder()
            .apply {
                if (mustClause.isNotEmpty()) must(mustClause)
                if (filterClause.isNotEmpty()) filter(filterClause)
            }
            .build()

        val response = client.search(
            { s -> s.index(PRODUCTS_INDEX).size(50).query { it.bool(boolQuery) } },
            Product::class.java
        )

        return response.hits().hits().mapNotNull { hit ->
            hit.source()?.let { source -> ProductSearchHit(product = source, score = hit.score()) }
        }
    }
}

/** DIKt.kt의 connectDatabase()/RedisClient.create()와 같은 자리 — 앱 시작 시 한 번 호출. */
fun createOpenSearchClient(host: String, port: Int): OpenSearchClient {
    val transport = ApacheHttpClient5TransportBuilder
        .builder(HttpHost("http", host, port))
        .setMapper(JacksonJsonpMapper(ObjectMapper().registerKotlinModule()))
        .build()
    return OpenSearchClient(transport)
}

/**
 * 명시적 매핑 — name만 text+keyword 서브필드(부분 검색+정렬 대비),
 * brandId는 필터 전용이라 정수 그대로, price는 향후 range 필터 확장 대비.
 */
fun ensureProductsIndex(client: OpenSearchClient) {
    val exists = client.indices().exists(ExistsRequest.Builder().index(PRODUCTS_INDEX).build()).value()
    if (exists) return

    val mapping = TypeMapping.Builder()
        .properties(
            "name",
            Property.Builder()
                .text(
                    TextProperty.Builder()
                        .fields("keyword", Property.Builder().keyword(KeywordProperty.Builder().build()).build())
                        .build()
                )
                .build()
        )
        .properties("brandId", Property.Builder().integer(IntegerNumberProperty.Builder().build()).build())
        .properties("price", Property.Builder().integer(IntegerNumberProperty.Builder().build()).build())
        .build()

    client.indices().create { req -> req.index(PRODUCTS_INDEX).mappings(mapping) }
}
