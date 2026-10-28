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

    // The JUnit dependencies come from the root build script, only the platform API is velocity specific.
    testImplementation(libs.velocity)
    testImplementation(libs.adventure)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

tasks.test {
    // The platform dependencies are compile-only, so the compiled fixtures need the test runtime classpath.
    systemProperty("winter.test.classpath", sourceSets.test.get().runtimeClasspath.asPath)
}
