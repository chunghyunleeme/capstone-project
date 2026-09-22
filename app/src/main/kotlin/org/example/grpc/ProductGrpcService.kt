package org.example.grpc

import io.grpc.Status
import io.grpc.StatusException
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines
import io.marqvision.capstone.ProductOuterClass
import io.marqvision.capstone.ProductServiceGrpcKt
import io.marqvision.capstone.product
import kotlinx.serialization.json.Json
import org.example.ProductCreateResult
import org.example.ProductRepository
import org.example.model.ProductWithBrand

private const val CACHE_TTL_SECONDS = 300L

/**
 * gRPC 표면. REST 라우트와 같은 ProductRepository를 쓰고,
 * 여기서는 도메인 모델 <-> protobuf 메시지 변환과 에러 코드 매핑만 한다.
 */
@OptIn(ExperimentalLettuceCoroutinesApi::class)
class ProductGrpcService(
    private val productRepository: ProductRepository,
    private val redisConnection: StatefulRedisConnection<String, String>,
    ) : ProductServiceGrpcKt.ProductServiceCoroutineImplBase() {

    override suspend fun getProduct(request: ProductOuterClass.GetProductRequest): ProductOuterClass.Product {
        val cacheKey = "cache:product:${request.id}"
        val redis = redisConnection.coroutines()

        val cached = redis.get(cacheKey)
        if (cached != null) {
           println("[cache] HIT $cacheKey")
           return Json.decodeFromString<ProductWithBrand>(cached).toProto()
        }

        println("[cache] MISS $cacheKey")
        val product = productRepository.findByIdWithBrand(request.id)
            ?: throw StatusException(
                Status.NOT_FOUND.withDescription("product ${request.id} not found")
            )

        redis.setex(cacheKey, CACHE_TTL_SECONDS, Json.encodeToString(product))

        return product.toProto()
    }

    override suspend fun createProduct(request: ProductOuterClass.CreateProductRequest): ProductOuterClass.Product {
        if (request.name.isBlank()) {
            throw StatusException(
                Status.INVALID_ARGUMENT.withDescription("name must not be blank")
            )
        }

        return when (
            val result = productRepository.create(
                brandId = request.brandId,
                name = request.name,
                price = request.price,
            )
        ) {
            is ProductCreateResult.Created -> {
                // create()는 Product를 돌려주지만 brand 정보가 없으므로,
                // proto 응답에 필요한 brand_id는 이미 요청값으로 알고 있어 그대로 씀
                product {
                    id = result.product.id
                    brandId = result.product.brandId
                    name = result.product.name
                    price = result.product.price
                }
            }
            is ProductCreateResult.BrandNotFound -> throw StatusException(
                Status.FAILED_PRECONDITION.withDescription("brand ${result.brandId} not found")
            )
        }
    }
}

private fun ProductWithBrand.toProto(): ProductOuterClass.Product = product {
    id = this@toProto.id
    brandId = this@toProto.brand.id
    name = this@toProto.name
    price = this@toProto.price
}