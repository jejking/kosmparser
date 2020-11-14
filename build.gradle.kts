import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import java.net.URI

val detektVersion = "1.9.1"
val junitJupiterVersion = "5.7.0"
val koTestVersion = "4.3.1"
val kotlinxCoRoutinesVersion = "1.4.1"

val sourceCompatibility = JavaVersion.VERSION_11

project.group = "com.jejking"
project.version = "0.0.1"

plugins {
    val kotlinVersion = "1.4.10"
    val ktlintVersion = "9.4.1"
    val testLoggerVersion = "2.1.1"
    val versionsVersion = "0.36.0"
    val detektVersion = "1.14.2"
    val dokkaVersion = "1.4.10.2"
    // Apply the Kotlin JVM plugin to add support for Kotlin.
    id("org.jetbrains.kotlin.jvm") version kotlinVersion
    id("org.jetbrains.dokka") version dokkaVersion
    id("org.jlleitschuh.gradle.ktlint") version ktlintVersion
    id("com.adarshr.test-logger") version testLoggerVersion
    id("com.github.ben-manes.versions") version versionsVersion
    id("io.gitlab.arturbosch.detekt") version detektVersion
    // Apply the java-library plugin for API and implementation separation.
    `java-library`
    `maven-publish`
}

repositories {
    // Use jcenter for resolving dependencies.
    // You can declare any Maven/Ivy/file repository here.
    jcenter()
    mavenCentral()
}

dependencies {
    // Align versions of all Kotlin components
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))

    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoRoutinesVersion")

    // Use the Kotlin JDK 8 standard library.
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    implementation("org.reactivestreams:reactive-streams:1.0.3")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:$kotlinxCoRoutinesVersion")

    implementation("com.fasterxml:aalto-xml:1.2.2")

    testImplementation("io.kotest:kotest-runner-junit5-jvm:$koTestVersion")
    testImplementation("io.kotest:kotest-assertions-core-jvm:$koTestVersion")
    testImplementation("io.kotest:kotest-property-jvm:$koTestVersion")

    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$kotlinxCoRoutinesVersion")

    testImplementation("com.github.tomakehurst:wiremock-jre8:2.27.2")
}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict")
            jvmTarget = sourceCompatibility
        }
    }

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

ktlint {
    version.set("0.39.0")
}

testlogger {
    setTheme("mocha")
}

fun isNonStable(version: String): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.toUpperCase().contains(it) }
    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
    val isStable = stableKeyword || regex.matches(version)
    return isStable.not()
}

tasks.withType<DependencyUpdatesTask> {
    rejectVersionIf {
        isNonStable(candidate.version)
    }
}

val sourcesJar by tasks.creating(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.getByName("main").allSource)
    from("LICENSE") {
        into("META-INF")
    }
}

val dokkaJavadocJar by tasks.creating(Jar::class) {
    dependsOn(tasks.dokkaJavadoc)
    from(tasks.dokkaJavadoc.get().outputDirectory)
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
            artifact(sourcesJar)
            artifact(dokkaJavadocJar)

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
