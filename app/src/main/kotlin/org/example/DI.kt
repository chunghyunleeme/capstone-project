package org.example

import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import org.example.db.BrandTable
import org.example.db.ProductTable
import org.jetbrains.exposed.v1.jdbc.Database

/**
 * 구현체 선택을 한곳에 모은다. 라우트는 인터페이스만 알고,
 * 어떤 구현이 꽂히는지는 여기서만 바뀐다.
 *
 * connect()는 provide 밖에서 호출한다. Exposed는 연결된 Database를 전역
 * 레지스트리에 등록하고 db 인자가 없으면 그중 첫 번째를 쓰는데,
 * provide 람다는 지연 실행이라 안에 두면 등록 자체가 미뤄진다.
 */
fun Application.configureDependencyInjection() {
    val database = connectDatabase(environment.config)
    // ProductTable이 BrandTable을 참조하므로 부모 테이블을 먼저 넘긴다.
    createSchema(database, BrandTable, ProductTable)

    dependencies {
        provide<Database> { database }
        provide<BrandRepository> { PostgresBrandRepository() }
        provide<ProductRepository> { PostgresProductRepository() }
    }
}
