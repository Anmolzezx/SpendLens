plugins {
    id("spendlens.jvm.library")
    application
}

application {
    mainClass.set("com.spendlens.server.MainKt")
}

dependencies {
    implementation(projects.core.protocol)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.sqlite.jdbc)
    runtimeOnly(libs.logback.classic)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.kotlinx.coroutines.test)
}

// `./gradlew :server:run` is for local development only: a fixed token, and a database file in this
// directory. A deployed server must be given its own SPENDLENS_TOKEN.
tasks.named<JavaExec>("run") {
    workingDir = projectDir
    environment("SPENDLENS_TOKEN", "dev-token")
}
