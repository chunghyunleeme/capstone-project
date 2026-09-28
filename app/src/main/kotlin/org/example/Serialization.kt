package org.example

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

/**
 * 생성 요청 본문. id는 DB가 매기므로 도메인 모델과 따로 둔다.
 */
@Serializable
data class BrandRequest(
    val name: String,
    val description: String? = null,
)

@Serializable
data class ProductRequest(
    val brandId: Int,
    val name: String,
    val price: Int,
)

@Serializable
data class ErrorResponse(val message: String)

/**
 * resolve()가 suspend라 모듈 자체를 suspend로 선언한다.
 * Ktor는 modules 리스트의 suspend 모듈도 그대로 호출해준다.
 */
suspend fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json()
    }

    val brandRepository = dependencies.resolve<BrandRepository>()
    val productRepository = dependencies.resolve<ProductRepository>()
    val productSearchRepository = dependencies.resolve<ProductSearchRepository>()

    routing {
        route("/brands") {
            get {
                call.respond(brandRepository.findAll())
            }

            post {
                val request = call.receive<BrandRequest>()
                val created = brandRepository.create(request.name, request.description)
                call.respond(HttpStatusCode.Created, created)
            }

            get("/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest)

                val brand = brandRepository.findById(id)
                    ?: return@get call.respond(HttpStatusCode.NotFound)

                call.respond(brand)
            }

            put("/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@put call.respond(HttpStatusCode.BadRequest)

                val request = call.receive<BrandRequest>()
                val updated = brandRepository.update(id, request.name, request.description)
                    ?: return@put call.respond(HttpStatusCode.NotFound)

                call.respond(updated)
            }

            delete("/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest)

                when (brandRepository.delete(id)) {
                    BrandDeleteResult.Deleted ->
                        call.respond(HttpStatusCode.NoContent)

                    BrandDeleteResult.NotFound ->
                        call.respond(HttpStatusCode.NotFound)

                    // FK(RESTRICT)가 막은 경우. 예외를 그대로 두면 500이 나간다.
                    BrandDeleteResult.HasProducts ->
                        call.respond(
                            HttpStatusCode.Conflict,
                            ErrorResponse("상품이 남아 있어 브랜드를 삭제할 수 없습니다."),
                        )
                }
            }
        }

        route("/products") {
            // 브랜드를 상품마다 따로 조회하지 않는다. 리포지토리가 JOIN 한 번으로 채워준다.
            get {
                call.respond(productRepository.findAllWithBrand())
            }

            // 브랜드 존재 확인은 라우트에서 하지 않는다.
            // 여기서 확인하면 확인과 생성이 서로 다른 트랜잭션이 되어
            // 그 사이에 브랜드가 삭제될 수 있다. 확인과 INSERT는 리포지토리가 한 트랜잭션에서 처리한다.
            post {
                val request = call.receive<ProductRequest>()

                when (val result = productRepository.create(request.brandId, request.name, request.price)) {
                    is ProductCreateResult.Created -> {
                        productSearchRepository.index(result.product)   // Postgres insert 직후 OpenSearch 동기화
                        call.respond(HttpStatusCode.Created, result.product)
                    }
                    is ProductCreateResult.BrandNotFound ->
                        call.respond(
                            HttpStatusCode.UnprocessableEntity,
                            ErrorResponse("brandId ${result.brandId}에 해당하는 브랜드가 없습니다."),
                        )
                }
            }

            get("/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest)

                val product = productRepository.findByIdWithBrand(id)
                    ?: return@get call.respond(HttpStatusCode.NotFound)

                call.respond(product)
            }

            put("/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@put call.respond(HttpStatusCode.BadRequest)

                val request = call.receive<ProductRequest>()

                when (val result = productRepository.update(id, request.brandId, request.name, request.price)) {
                    is ProductUpdateResult.Updated ->
                        call.respond(result.product)

                    ProductUpdateResult.NotFound ->
                        call.respond(HttpStatusCode.NotFound)

                    is ProductUpdateResult.BrandNotFound ->
                        call.respond(
                            HttpStatusCode.UnprocessableEntity,
                            ErrorResponse("brandId ${result.brandId}에 해당하는 브랜드가 없습니다."),
                        )
                }
            }

            delete("/{id}") {
                val id = call.parameters["id"]?.toIntOrNull()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest)

                if (productRepository.delete(id)) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }

            get("/search") {
                val q = call.request.queryParameters["q"]
                val brandId = call.request.queryParameters["brandId"]?.toIntOrNull()
                call.respond(productSearchRepository.search(q, brandId))
            }
        }
    }
}