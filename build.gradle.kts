import org.gradle.external.javadoc.StandardJavadocDocletOptions

plugins {
    id("java")
    id("maven-publish")
    id("io.deepmedia.tools.deployer") version "0.16.0"
    signing
}

if (System.getenv("JITPACK") == "true" && group.toString().startsWith("com.github.") && !group.toString().endsWith(".${rootProject.name}")) {
    group = "${group}.${rootProject.name}"
}

val catalog = libs

subprojects {
    group = rootProject.group
    version = rootProject.version

    apply(plugin = "java")
    apply(plugin = "io.deepmedia.tools.deployer")

    java {
        withJavadocJar()
        withSourcesJar()
    }

    dependencies {
        testImplementation(catalog.junit.api)
        testRuntimeOnly(catalog.junit.engine)
        testRuntimeOnly(catalog.junit.platform.launcher)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    tasks.withType<Javadoc>().configureEach {
        options.encoding = "UTF-8"
        options.quiet()
        (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:none", true)
    }

    deployer {
        signing {
            key.set(secret("winterSigningKey"))
            password.set(secret("winterSigningPassphrase"))
        }

        content {
            component {
                fromJava()
            }
        }

        projectInfo {
            description.set("The framework to make plugin creation easier than ever.")
            url.set("https://github.com/thewinterframework/winter")
            license(MIT)

            groupId.set(group.toString())
            scm.fromGithub("thewinterframework", "winter")
            developer("Diego Cardenas", "diego.cardenas.v06@gmail.com")
            artifactId.set(project.name)
        }

        centralPortalSpec {
            signing.key.set(secret("winterSigningKey"))
            signing.password.set(secret("winterSigningPassphrase"))
            auth.user.set(secret("winterRepositoryUsername"))
            auth.password.set(secret("winterRepositoryPassword"))
        }
    }
}

repositories {
    mavenCentral()
}

