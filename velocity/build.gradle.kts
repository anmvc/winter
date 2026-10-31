plugins {
    id("java")
    `java-library`
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnlyApi(libs.velocity)
    api(project(":core"))
    annotationProcessor(project(":core"))

    compileOnlyApi(libs.auto.service.annotations)
    annotationProcessor(libs.auto.service)
    implementation(libs.snakeyaml)

    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.velocity)
    testImplementation(libs.adventure)
}

tasks.test {
    systemProperty("winter.test.classpath", sourceSets.test.get().runtimeClasspath.asPath)
}
