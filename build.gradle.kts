import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI

plugins {
    kotlin("jvm")
    id("io.gitlab.arturbosch.detekt")
    id("com.github.ben-manes.versions")
    id("com.adarshr.test-logger")
    id("org.jetbrains.dokka")
    id("org.jetbrains.dokka-javadoc")
    `maven-publish`
}

val kotlinVersion: String by project
val kotestVersion: String by project
val kotlinxCoRoutinesVersion: String by project
val aaltoXmlVersion: String by project
val wiremockVersion: String by project
val reactiveStreamsVersion: String by project

project.group = "com.jejking"
project.version = "0.0.3"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
    compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoRoutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:$kotlinxCoRoutinesVersion")
    implementation("org.reactivestreams:reactive-streams:$reactiveStreamsVersion")
    implementation("com.fasterxml:aalto-xml:$aaltoXmlVersion")

    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest:kotest-property:$kotestVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$kotlinxCoRoutinesVersion")
    testImplementation("org.wiremock:wiremock:$wiremockVersion")
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

tasks.register<Jar>("dokkaHtmlJar") {
    dependsOn(tasks.dokkaGeneratePublicationHtml)
    from(tasks.dokkaGeneratePublicationHtml.flatMap { it.outputDirectory })
    archiveClassifier.set("html-docs")
}

tasks.register<Jar>("dokkaJavadocJar") {
    dependsOn(tasks.dokkaGeneratePublicationJavadoc)
    from(tasks.dokkaGeneratePublicationJavadoc.flatMap { it.outputDirectory })
    archiveClassifier.set("javadoc")
}

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
             artifact(tasks["dokkaJavadocJar"])

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
