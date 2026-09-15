package org.example

import org.example.db.BrandTable
import org.example.db.ProductTable
import org.example.model.Brand
import org.example.model.Product
import org.example.model.ProductWithBrand
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

/**
 * 생성 결과. 없는 브랜드를 참조한 경우를 예외가 아닌 값으로 돌려줘야
 * 라우트가 500이 아닌 4xx로 응답할 수 있다.
 */
sealed interface ProductCreateResult {
    data class Created(val product: Product) : ProductCreateResult
    data class BrandNotFound(val brandId: Int) : ProductCreateResult
}

/**
 * 수정 결과. 생성과 마찬가지로 없는 브랜드 참조를 값으로 구분한다.
 */
sealed interface ProductUpdateResult {
    data class Updated(val product: Product) : ProductUpdateResult
    data object NotFound : ProductUpdateResult
    data class BrandNotFound(val brandId: Int) : ProductUpdateResult
}

interface ProductRepository {
    suspend fun create(brandId: Int, name: String, price: Int): ProductCreateResult
    suspend fun findAllWithBrand(): List<ProductWithBrand>
    suspend fun findByIdWithBrand(id: Int): ProductWithBrand?
    suspend fun update(id: Int, brandId: Int, name: String, price: Int): ProductUpdateResult
    suspend fun delete(id: Int): Boolean
}

class PostgresProductRepository : ProductRepository {

    /**
     * 브랜드 존재 확인과 INSERT를 한 트랜잭션에 넣는다.
     * 라우트에서 findById 후 create를 부르면 트랜잭션이 둘로 쪼개져
     * 그 사이에 브랜드가 삭제될 수 있으므로, 두 작업을 여기서 함께 처리한다.
     *
     * 그래도 남는 경쟁 구간은 FK 제약이 막고, 그때 터지는 예외는
     * 트랜잭션이 롤백된 뒤(= withTransaction 바깥에서) 값으로 변환한다.
     */
    override suspend fun create(brandId: Int, name: String, price: Int): ProductCreateResult =
        try {
            withTransaction {
                val brandMissing = BrandTable
                    .selectAll()
                    .where { BrandTable.id eq brandId }
                    .limit(1)
                    .empty()

                if (brandMissing) {
                    ProductCreateResult.BrandNotFound(brandId)
                } else {
                    val generatedId = ProductTable.insert {
                        it[ProductTable.brandId] = brandId
                        it[ProductTable.name] = name
                        it[ProductTable.price] = price
                    } get ProductTable.id

                    ProductCreateResult.Created(Product(generatedId, brandId, name, price))
                }
            }
        } catch (e: ExposedSQLException) {
            if (e.sqlState in REFERENTIAL_INTEGRITY_VIOLATION) {
                ProductCreateResult.BrandNotFound(brandId)
            } else {
                throw e
            }
        }

    /**
     * 상품 N건에 대해 브랜드를 N번 조회하면 N+1이므로,
     * JOIN 한 번으로 양쪽 컬럼을 같이 읽는다.
     */
    override suspend fun findAllWithBrand(): List<ProductWithBrand> = withTransaction {
        ProductTable
            .innerJoin(BrandTable)
            .selectAll()
            .map(::toProductWithBrand)
    }

    /** 목록과 같은 JOIN을 쓰되 한 건만 좁혀 읽는다. */
    override suspend fun findByIdWithBrand(id: Int): ProductWithBrand? = withTransaction {
        ProductTable
            .innerJoin(BrandTable)
            .selectAll()
            .where { ProductTable.id eq id }
            .limit(1)
            .map(::toProductWithBrand)
            .singleOrNull()
    }

    /** 생성과 같은 이유로 브랜드 확인과 UPDATE를 한 트랜잭션에 둔다. */
    override suspend fun update(id: Int, brandId: Int, name: String, price: Int): ProductUpdateResult =
        try {
            withTransaction {
                val brandMissing = BrandTable
                    .selectAll()
                    .where { BrandTable.id eq brandId }
                    .limit(1)
                    .empty()

                if (brandMissing) {
                    return@withTransaction ProductUpdateResult.BrandNotFound(brandId)
                }

                val updatedRows = ProductTable.update({ ProductTable.id eq id }) {
                    it[ProductTable.brandId] = brandId
                    it[ProductTable.name] = name
                    it[ProductTable.price] = price
                }

                if (updatedRows > 0) {
                    ProductUpdateResult.Updated(Product(id, brandId, name, price))
                } else {
                    ProductUpdateResult.NotFound
                }
            }
        } catch (e: ExposedSQLException) {
            if (e.sqlState in REFERENTIAL_INTEGRITY_VIOLATION) {
                ProductUpdateResult.BrandNotFound(brandId)
            } else {
                throw e
            }
        }

    override suspend fun delete(id: Int): Boolean = withTransaction {
        ProductTable.deleteWhere { ProductTable.id eq id } > 0
    }

    private fun toProductWithBrand(row: ResultRow) = ProductWithBrand(
        id = row[ProductTable.id],
        name = row[ProductTable.name],
        price = row[ProductTable.price],
        brand = Brand(
            id = row[BrandTable.id],
            name = row[BrandTable.name],
            description = row[BrandTable.description],
        ),
    )
}
