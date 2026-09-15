# capstone-project

Ktor + Exposed + PostgreSQL로 만든 브랜드/상품 REST API입니다.

## 요구 사항

- JDK 21 이상 (Gradle 툴체인이 자동으로 해결)
- Docker / Docker Compose
- Gradle은 별도 설치 불필요 — 래퍼(`./gradlew`)를 사용

## 실행

```bash
docker compose up --build
```

앱은 8080, PostgreSQL은 호스트의 15432 포트에 뜹니다. 기동 시 `brands`·`products` 테이블이 없으면 생성됩니다.

```bash
curl localhost:8080/brands
curl -X POST localhost:8080/brands \
  -H 'Content-Type: application/json' \
  -d '{"name":"nike","description":"스포츠"}'
```

로컬에서 앱만 따로 띄우려면 DB 컨테이너만 올린 뒤 `./gradlew run`을 쓰면 됩니다. 이때는 `application.yaml`의 기본값에 따라 `localhost:15432`로 붙습니다.

## API

| 메서드 | 경로 | 성공 | 실패 |
| --- | --- | --- | --- |
| GET | `/brands` | 200 | |
| POST | `/brands` | 201 | |
| GET | `/brands/{id}` | 200 | 404, 400 |
| PUT | `/brands/{id}` | 200 | 404, 400 |
| DELETE | `/brands/{id}` | 204 | 404, 409, 400 |
| GET | `/products` | 200 | |
| POST | `/products` | 201 | 422 |
| GET | `/products/{id}` | 200 | 404, 400 |
| PUT | `/products/{id}` | 200 | 404, 422, 400 |
| DELETE | `/products/{id}` | 204 | 404, 400 |

- **400** — 경로의 `{id}`가 정수가 아님
- **409** — 상품이 남아 있는 브랜드를 삭제하려 함
- **422** — 존재하지 않는 `brandId`를 참조

상품 조회 응답에는 브랜드가 중첩되어 담깁니다.

```json
[{"id":1,"name":"air-max","price":159000,
  "brand":{"id":2,"name":"nike","description":"스포츠"}}]
```

## 설정

`app/src/main/resources/application.yaml`이 포트, 실행할 모듈 목록, DB 접속 정보를 담습니다. 이 파일은 `mainClass`가 `io.ktor.server.netty.EngineMain`이기 때문에 자동으로 로드됩니다.

```yaml
storage:
  jdbcURL: "$DATABASE_JDBC_URL:jdbc:postgresql://localhost:15432/ktor_tutorial_db"
```

`"$VAR:기본값"` 문법이라, 컨테이너 안에서는 compose가 넘기는 `DATABASE_JDBC_URL`(호스트 `db`)을, 로컬에서는 뒤의 기본값을 씁니다.

## 설계 메모

**트랜잭션 경계는 리포지토리에 둡니다.** 라우트에서 브랜드 존재를 확인한 뒤 상품 생성을 호출하면 두 작업이 서로 다른 트랜잭션이 되어, 그 사이에 브랜드가 삭제될 수 있습니다. 확인과 INSERT/UPDATE는 리포지토리의 한 `withTransaction` 안에서 처리합니다.

**목록 조회는 JOIN 한 번입니다.** 상품마다 브랜드를 따로 읽으면 N+1이므로 `ProductTable.innerJoin(BrandTable)`로 한 번에 읽습니다.

**참조 무결성 위반은 4xx로 내려갑니다.** DB 예외를 그대로 흘리면 500이 나가므로, 리포지토리가 sealed 타입으로 결과를 돌려주고 라우트가 상태 코드를 정합니다. 이때 SQLSTATE가 둘로 갈린다는 점에 주의해야 합니다 — 없는 부모를 참조하는 INSERT/UPDATE는 `23503`, RESTRICT가 막은 부모 DELETE는 `23001`입니다.

**JDBC는 블로킹입니다.** `withTransaction`이 `Dispatchers.IO`로 전환해 요청 처리 스레드를 막지 않습니다. 같은 헬퍼가 `StdOutSqlLogger`를 붙여 실행된 SQL을 콘솔에 남깁니다.

## 프로젝트 구조

| 경로 | 설명 |
| --- | --- |
| `app/src/main/resources/application.yaml` | 포트, 모듈 목록, DB 접속 정보 |
| `app/src/main/kotlin/org/example/Exposed.kt` | DB 연결, 스키마 생성, `withTransaction` |
| `app/src/main/kotlin/org/example/DI.kt` | `Database`와 리포지토리 등록 |
| `app/src/main/kotlin/org/example/Serialization.kt` | ContentNegotiation 설치 및 라우트 |
| `app/src/main/kotlin/org/example/BrandRepository.kt` | 브랜드 저장소 인터페이스/구현 |
| `app/src/main/kotlin/org/example/ProductRepository.kt` | 상품 저장소 인터페이스/구현 |
| `app/src/main/kotlin/org/example/db/` | Exposed 테이블 정의 |
| `app/src/main/kotlin/org/example/model/` | 직렬화되는 도메인 모델 |
| `app/src/main/kotlin/org/example/App.kt` | 초기 코루틴 예제. 더 이상 진입점이 아닙니다 |
| `Dockerfile`, `compose.yml` | 컨테이너 실행 |

## 빌드

```bash
./gradlew build          # 컴파일 + 검증
./gradlew buildFatJar    # app/build/libs/app-all.jar 생성
```

## 주요 의존성

- Ktor 3.5.2 — server-core, netty, content-negotiation, DI, config-yaml
- Exposed 1.5.0 — core, jdbc, dao
- PostgreSQL JDBC 42.7.13, kotlinx-serialization 1.11.0, Logback 1.6.3
- Kotlin JVM 플러그인 2.4.0, Gradle 9.7.1
- JUnit 5 / `kotlin-test` — 테스트 설정만 되어 있고 아직 작성된 테스트는 없습니다
