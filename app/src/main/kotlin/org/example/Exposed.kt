package org.example

import io.ktor.server.config.ApplicationConfig
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlinx.coroutines.withContext

/**
 * 참조 무결성 위반 SQLSTATE.
 * 23503은 없는 부모를 참조하는 INSERT/UPDATE,
 * 23001은 RESTRICT가 막은 부모 DELETE다. 둘은 다른 코드로 올라온다.
 */
val REFERENTIAL_INTEGRITY_VIOLATION = setOf("23503", "23001")

/**
 * storage 섹션을 읽어 Exposed Database 핸들을 만든다. DI 등록은 DI.kt에서 한다.
 * connect()는 실제 커넥션을 열지 않고, 트랜잭션이 필요할 때 열 방법만 등록해둔다.
 */
fun connectDatabase(config: ApplicationConfig): Database {
    val jdbcURL = config.property("storage.jdbcURL").getString()
    val user = config.property("storage.user").getString()
    val password = config.property("storage.password").getString()

    return Database.connect(
        url = jdbcURL,
        driver = "org.postgresql.Driver",
        user = user,
        password = password,
    )
}

/**
 * 없는 테이블만 CREATE TABLE 한다. 기동 시점이라 suspend가 아닌 transaction을 쓴다.
 * 컬럼 변경은 감지하지 못하므로, 스키마가 굳으면 마이그레이션 도구로 옮겨야 한다.
 */
fun createSchema(db: Database, vararg tables: Table) = transaction(db) {
    addLogger(StdOutSqlLogger)
    SchemaUtils.create(*tables)
}

/**
 * JDBC는 블로킹이라 IO 디스패처로 넘겨야 Ktor의 요청 처리 스레드를 막지 않는다.
 * 블록 안에서 실행된 SQL은 StdOutSqlLogger가 그대로 콘솔에 찍어준다.
 */
suspend fun <T> withTransaction(
    db: Database? = null,
    block: suspend JdbcTransaction.() -> T,
): T = withContext(Dispatchers.IO) {
    suspendTransaction(db) {
        addLogger(StdOutSqlLogger)
        block()
    }
}