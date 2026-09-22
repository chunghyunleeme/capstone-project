package org.example.grpc

import io.grpc.ServerBuilder
import io.grpc.protobuf.services.ProtoReflectionServiceV1
import io.ktor.server.application.Application
import io.ktor.server.plugins.di.dependencies
import io.lettuce.core.api.StatefulRedisConnection
import org.example.BrandRepository
import org.example.ProductRepository

/**
 * gRPC는 HTTP/2 전용이라 Ktor(Netty, HTTP/1.1) 엔진과 별개 서버로 띄운다.
 * Ktor의 modules 리스트에 등록해, HTTP 서버 부트 시점에 같이 기동한다.
 */
suspend fun Application.configureGrpc() {
    val brandRepository = dependencies.resolve<BrandRepository>()
    val productRepository = dependencies.resolve<ProductRepository>()
    val redisConnection = dependencies.resolve<StatefulRedisConnection<String, String>>()

    val grpcServer = ServerBuilder.forPort(9090)
        .addService(BrandGrpcService(brandRepository))
        .addService(ProductGrpcService(productRepository, redisConnection))
        .addService(ProtoReflectionServiceV1.newInstance())
        .build()
        .start()

    // Ktor 프로세스가 죽을 때 gRPC 서버도 같이 내려가도록 훅을 건다.
    Runtime.getRuntime().addShutdownHook(Thread { grpcServer.shutdown() })

    println("gRPC server started, listening on 9090")
}