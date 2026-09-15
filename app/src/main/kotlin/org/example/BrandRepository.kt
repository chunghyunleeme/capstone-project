package org.example

import org.example.db.BrandTable
import org.example.model.Brand
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update

/**
 * 삭제 결과. 상품이 딸린 브랜드는 FK(RESTRICT)가 막는데,
 * 그 예외를 그대로 흘리면 500이 나가므로 값으로 구분해 돌려준다.
 */
sealed interface BrandDeleteResult {
    data object Deleted : BrandDeleteResult
    data object NotFound : BrandDeleteResult
    data object HasProducts : BrandDeleteResult
}

/**
 * 브랜드 저장소 경계. 라우트는 이 인터페이스만 알고 Exposed를 모른다.
 */
interface BrandRepository {
    suspend fun create(name: String, description: String?): Brand
    suspend fun findAll(): List<Brand>
    suspend fun findById(id: Int): Brand?
    /** 수정된 브랜드. 대상이 없으면 null. */
    suspend fun update(id: Int, name: String, description: String?): Brand?
    suspend fun delete(id: Int): BrandDeleteResult
}

/**
 * Exposed DSL 구현. 모든 쿼리는 withTransaction으로 감싸 IO 디스패처에서 돈다.
 */
class PostgresBrandRepository : BrandRepository {

    override suspend fun create(name: String, description: String?): Brand = withTransaction {
        // autoIncrement 컬럼이라 DB가 만든 id를 insert 결과에서 되받는다.
        val generatedId = BrandTable.insert {
            it[BrandTable.name] = name
            it[BrandTable.description] = description
        } get BrandTable.id

        Brand(generatedId, name, description)
    }

    override suspend fun findAll(): List<Brand> = withTransaction {
        BrandTable.selectAll().map(::toBrand)
    }

    override suspend fun findById(id: Int): Brand? = withTransaction {
        BrandTable
            .selectAll()
            .where { BrandTable.id eq id }
            .limit(1)
            .map(::toBrand)
            .singleOrNull()
    }

    override suspend fun update(id: Int, name: String, description: String?): Brand? = withTransaction {
        val updatedRows = BrandTable.update({ BrandTable.id eq id }) {
            it[BrandTable.name] = name
            it[BrandTable.description] = description
        }
        if (updatedRows > 0) Brand(id, name, description) else null
    }

    override suspend fun delete(id: Int): BrandDeleteResult =
        try {
            withTransaction {
                if (BrandTable.deleteWhere { BrandTable.id eq id } > 0) {
                    BrandDeleteResult.Deleted
                } else {
                    BrandDeleteResult.NotFound
                }
            }
        } catch (e: ExposedSQLException) {
            if (e.sqlState in REFERENTIAL_INTEGRITY_VIOLATION) {
                BrandDeleteResult.HasProducts
            } else {
                throw e
            }
        }

    private fun toBrand(row: ResultRow) = Brand(
        id = row[BrandTable.id],
        name = row[BrandTable.name],
        description = row[BrandTable.description],
    )
}