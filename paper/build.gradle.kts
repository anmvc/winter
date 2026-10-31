plugins {
    id("java")
    `java-library`
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    maven("https://repo.kryptonmc.org/releases")
}

dependencies {
    // Platform
    compileOnlyApi(libs.paper)

    // Core
    api(project(":core"))
    annotationProcessor(project(":core"))

    compileOnlyApi(libs.auto.service.annotations)
    annotationProcessor(libs.auto.service)

    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}
