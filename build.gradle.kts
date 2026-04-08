import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.detekt)
    alias(libs.plugins.versions)
    alias(libs.plugins.test.logger)
    alias(libs.plugins.dokka)
    alias(libs.plugins.protobuf)
    jacoco
    `maven-publish`
}

project.group = "com.jejking"
project.version = "0.0.3"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
    compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

detekt {
    toolVersion = libs.versions.detekt.get()
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "21"
    jdkHome.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
        }.get().executablePath.asFile.parentFile.parentFile
    )
    // Exclude generated protobuf Kotlin DSL sources
    exclude("**/build/generated/**")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("kotlin")
            }
        }
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.reactive)
    implementation(libs.reactive.streams)
    implementation(libs.aalto.xml)
    implementation(libs.protobuf.kotlin)

    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.property)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.wiremock)
}

tasks {
    withType<Test> {
        useJUnitPlatform()
    }

     withType<Jar> {
         archiveBaseName.set(rootProject.name)

         manifest {
             attributes(
                 mapOf(
                     "Implementation-Title" to project.name,
                     "Implementation-Version" to project.version,
                     "Class-Path" to configurations.compileClasspath.get().joinToString(" ") { it.name }
                 )
             )
         }
     }
}


testlogger {
    setTheme("mocha")
}

jacoco {
    toolVersion = "0.8.12"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    // Exclude generated protobuf code from coverage reporting
    classDirectories.setFrom(
        files(classDirectories.files.map { dir ->
            fileTree(dir) {
                exclude("**/crosby/binary/**")
            }
        })
    )
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    classDirectories.setFrom(
        files(classDirectories.files.map { dir ->
            fileTree(dir) {
                exclude("**/crosby/binary/**")
            }
        })
    )
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.88".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                minimum = "0.70".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
    val isStable = stableKeyword || regex.matches(version)
    return isStable.not()
}

tasks.withType<DependencyUpdatesTask> {
    rejectVersionIf {
        isNonStable(candidate.version)
    }
}

tasks.kotlinSourcesJar {
    from("LICENSE") {
        into("META-INF")
    }
    manifest {
        attributes(
            mapOf(
                "Implementation-Title" to project.name,
                "Implementation-Version" to project.version,
            )
        )
    }
}


// Dokka publication tasks are handled by the dokka plugin directly

// publication

 val artifactName = rootProject.name
 val artifactGroup = project.group.toString()
 val artifactVersion = project.version.toString()

 val pomUrl = "https://github.com/jejking/kosmparser"
 val pomScmUrl = "https://github.com/jejking/kosmparser"
 val pomIssueUrl = "https://github.com/jejking/kosmparser/issues"
 val pomDesc = "Asynchronous streaming parser for OSM XML"

 val githubRepo = "jejking/kosmparser"
 val githubReadme = "README.md"

 val pomLicenseName = "Apache 2.0"
 val pomLicenseUrl = "https://www.apache.org/licenses/LICENSE-2.0"
 val pomLicenseDist = "repo"

 val pomDeveloperId = "jejking"
 val pomDeveloperName = "John King"

 publishing {
     publications {
         create<MavenPublication>("kosmparser") {
             groupId = artifactGroup
             artifactId = artifactName
             version = artifactVersion
             from(components["java"])
             artifact(tasks.kotlinSourcesJar)

             pom {
                 packaging = "jar"
                 name.set(rootProject.name)
                 description.set(pomDesc)
                 url.set(pomUrl)
                 scm {
                     url.set(pomScmUrl)
                 }
                 issueManagement {
                     url.set(pomIssueUrl)
                 }
                 licenses {
                     license {
                         name.set(pomLicenseName)
                         url.set(pomLicenseUrl)
                     }
                 }
                 developers {
                     developer {
                         id.set(pomDeveloperId)
                         name.set(pomDeveloperName)
                     }
                 }
             }
         }
     }
     repositories {
         maven {
             name = "GitHubPackages"
             url = URI.create("https://maven.pkg.github.com/jejking/kosmparser")
             credentials {
                 username = System.getenv("GITHUB_ACTOR")
                 password = System.getenv("GITHUB_TOKEN")
             }
         }
     }
 }
