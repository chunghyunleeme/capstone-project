package org.example.grpc

import io.grpc.Status
import io.grpc.StatusException
import io.marqvision.capstone.BrandOuterClass
import io.marqvision.capstone.BrandServiceGrpcKt
import io.marqvision.capstone.brand
import org.example.BrandRepository
import org.example.model.Brand

/**
 * gRPC 표면. REST 라우트와 같은 BrandRepository를 쓰고,
 * 여기서는 도메인 모델 <-> protobuf 메시지 변환과 에러 코드 매핑만 한다.
 */
class BrandGrpcService(
    private val brandRepository: BrandRepository,
) : BrandServiceGrpcKt.BrandServiceCoroutineImplBase() {

    override suspend fun getBrand(request: BrandOuterClass.GetBrandRequest): BrandOuterClass.Brand =
        brandRepository.findById(request.id)?.toProto()
        // 없는 id는 예외가 아니라 NOT_FOUND 상태로 돌려준다. 그냥 두면 UNKNOWN이 나간다.
            ?: throw StatusException(
                Status.NOT_FOUND.withDescription("brand ${request.id} not found")
            )

    override suspend fun createBrand(request: BrandOuterClass.CreateBrandRequest): BrandOuterClass.Brand {
        // proto3는 string 기본값이 ""라 "미지정"과 구분되지 않는다. 필수 값은 직접 검증해야 한다.
        if (request.name.isBlank()) {
            throw StatusException(
                Status.INVALID_ARGUMENT.withDescription("name must not be blank")
            )
        }

        return brandRepository.create(
            name = request.name,
            // optional 필드라 hasDescription()으로 "빈 문자열"과 "없음"을 가른다.
            description = if (request.hasDescription()) request.description else null,
        ).toProto()
    }
}

private fun Brand.toProto(): BrandOuterClass.Brand = brand {
    id = this@toProto.id
    name = this@toProto.name
    this@toProto.description?.let { description = it }
}
