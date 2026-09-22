plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.protobuf)
    // api 구성을 쓰려면 필요하다. kotlin-jvm은 java 플러그인만 적용한다.
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    // 생성된 스텁의 공개 시그니처(부모 클래스, 메시지 타입)에 드러나므로 api여야
    // 소비 모듈이 스텁을 상속·호출할 수 있다.
    api(libs.grpc.kotlin.stub)
    api(libs.grpc.protobuf)
    api(libs.grpc.stub)
    api(libs.protobuf.kotlin)

    // 런타임 전송 계층. 스텁의 공개 API에는 나타나지 않는다.
    implementation(libs.grpc.netty)
}

kotlin {
    jvmToolchain(17)
}

protobuf {
    protoc {
        artifact = libs.protoc.asProvider().get().toString()
    }
    plugins {
        create("grpc") {
            artifact = libs.protoc.gen.grpc.java.get().toString()
        }
        create("grpckt") {
            artifact = "${libs.protoc.gen.grpc.kotlin.get()}:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                create("grpc")
                create("grpckt")
            }
            it.builtins {
                create("kotlin")
            }
        }
    }
}
