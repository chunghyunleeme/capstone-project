package org.example.model

import kotlinx.serialization.Serializable

/**
 * 도메인 모델. 테이블 컬럼과 1:1이지만 Exposed 타입에 의존하지 않아
 * 그대로 JSON 응답으로 내보낼 수 있다.
 */
@Serializable
data class Brand(
    val id: Int,
    val name: String,
    val description: String? = null,
)