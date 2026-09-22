// 서브프로젝트가 쓰는 플러그인을 루트에서 한 번만 로드한다.
// apply false: 루트 자체에는 적용하지 않고 classpath에만 올려, 서브프로젝트마다
// 별도 classloader로 중복 로드되는 것("loaded multiple times") 을 막는다.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ktor) apply false
    alias(libs.plugins.protobuf) apply false
}
