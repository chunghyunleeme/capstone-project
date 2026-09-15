package org.example.db

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

/**
 * products 테이블. brand_id에 실제 FK 제약을 건다.
 *
 * 애플리케이션 코드의 존재 확인은 경쟁 상황에서 뚫릴 수 있으므로,
 * "없는 브랜드를 참조할 수 없다"는 규칙은 DB가 최종적으로 보장하게 둔다.
 * RESTRICT라서 상품이 딸린 브랜드는 삭제되지 않는다.
 */
object ProductTable : Table("products") {
    val id = integer("id").autoIncrement()
    val brandId = integer("brand_id")
        .references(BrandTable.id, onDelete = ReferenceOption.RESTRICT)
    val name = varchar("name", 255)
    val price = integer("price")

    override val primaryKey = PrimaryKey(id)
}