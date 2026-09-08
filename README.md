# capstone-project

Kotlin 코루틴으로 여러 URL을 **동시에** 요청하고 각 응답의 상태 코드와 소요 시간을 측정하는 CLI 예제입니다.

## 요구 사항

- JDK 21 이상 (Gradle 툴체인이 자동으로 해결)
- Gradle은 별도 설치 불필요 — 래퍼(`./gradlew`)를 사용

## 실행

```bash
./gradlew run
```

출력 예시:

```
Fetching 5 URLs concurrently...

[200] https://www.google.com - 312ms
[200] https://www.github.com - 428ms
[200] https://kotlinlang.org - 205ms
[200] https://www.wikipedia.org - 341ms
[200] https://httpbin.org/delay/1 - 1187ms

Total time: 1194ms
```

전체 소요 시간이 가장 느린 요청 하나의 시간에 가깝다는 점이 핵심입니다. 순차 실행이라면 각 요청 시간의 합이 됩니다.

## 동작 방식

```
urls.map { async { fetchUrl(it) } }   // ① 요청을 전부 먼저 띄운다
    .awaitAll()                       // ② 결과는 나중에 몰아서 받는다
```

`async`가 코루틴을 즉시 시작시키므로 ①에서 5개 요청이 모두 병렬로 진행되고, ②의 `awaitAll()`은 이미 돌고 있는 작업들이 끝나기를 기다릴 뿐입니다. `async { ... }.await()`을 루프 안에서 바로 호출하면 매번 완료를 기다리게 되어 사실상 순차 실행이 됩니다.

블로킹 I/O인 `HttpURLConnection`은 `withContext(Dispatchers.IO)`로 감싸 I/O 전용 스레드 풀에서 실행합니다. 연결·읽기 타임아웃은 각각 5초입니다.

## 프로젝트 구조

| 경로 | 설명 |
| --- | --- |
| `app/src/main/kotlin/org/example/App.kt` | 진입점 (`suspend fun main`), `fetchUrl`, `FetchResult` |
| `app/build.gradle.kts` | 애플리케이션 모듈 빌드 설정 |
| `gradle/libs.versions.toml` | 의존성 버전 카탈로그 |
| `settings.gradle.kts` | 루트 프로젝트 및 모듈 구성 |

## 빌드

```bash
./gradlew build          # 컴파일 + 검증
./gradlew installDist    # app/build/install/app/bin/app 실행 스크립트 생성
```

## 주요 의존성

- `kotlinx-coroutines-core` 1.11.0 — 코루틴
- Kotlin JVM 플러그인 2.4.0, Gradle 9.7.1
- JUnit 5 / `kotlin-test` — 테스트 설정만 되어 있고 아직 작성된 테스트는 없습니다
