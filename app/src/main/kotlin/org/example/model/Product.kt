package org.example.model

import kotlinx.serialization.Serializable

@Serializable
data class Product(
    val id: Int,
    val brandId: Int,
    val name: String,
    val price: Int,
)

/**
 * 목록 응답용. 상품마다 브랜드를 따로 조회하지 않고
 * JOIN 한 번으로 채운 결과를 담는다.
 */
@Serializable
data class ProductWithBrand(
    val id: Int,
    val name: String,
    val price: Int,
    val brand: Brand,
)