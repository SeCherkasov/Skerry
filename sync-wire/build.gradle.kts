plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinxSerialization)
}

group = "app.skerry"
version = "0.1.0"

kotlin {
    jvmToolchain(21)
}

dependencies {
    // api: @Serializable-типы контракта видны потребителям вместе с сериализаторами.
    api(libs.kotlinx.serialization.json)
}
