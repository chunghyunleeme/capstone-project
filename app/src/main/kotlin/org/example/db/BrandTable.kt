package org.example.db

import org.jetbrains.exposed.v1.core.Table

/**
 * brands 테이블의 DSL 정의. DAO를 쓰지 않으므로 id도 평범한 컬럼으로 직접 선언한다.
 */
object BrandTable : Table("brands") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 255)
    val description = text("description").nullable()

    override val primaryKey = PrimaryKey(id)
}