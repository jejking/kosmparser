import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

val detektVersion = "1.9.1"
val junitJupiterVersion = "5.7.0"
val koTestVersion = "4.2.5"
val kotlinxCoRoutinesVersion = "1.3.9"

val sourceCompatibility = JavaVersion.VERSION_11

plugins {
  val kotlinVersion = "1.4.10"
  val ktlintVersion = "9.4.0"
  val testLoggerVersion = "2.1.0"
  val versionsVersion = "0.33.0"
  val detektVersion = "1.14.0"
  // Apply the Kotlin JVM plugin to add support for Kotlin.
  id("org.jetbrains.kotlin.jvm") version kotlinVersion
  id("org.jlleitschuh.gradle.ktlint") version ktlintVersion
  id("com.adarshr.test-logger") version testLoggerVersion
  id("com.github.ben-manes.versions") version versionsVersion
  id("io.gitlab.arturbosch.detekt") version detektVersion
  // Apply the java-library plugin for API and implementation separation.
  `java-library`
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

  // Use the Kotlin JDK 8 standard library.
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoRoutinesVersion")

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
