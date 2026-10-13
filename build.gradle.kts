plugins {
  kotlin("jvm") version "2.4.21"
  kotlin("plugin.serialization") version "2.4.21"
  id("com.gradleup.shadow") version "9.6.1"
}

group = "ru.mcbuddy"
// The release workflow passes the version; every other build is a dev build.
version = (findProperty("releaseVersion") as String?)?.takeIf { it.isNotBlank() } ?: "dev"

repositories {
  mavenCentral()
  maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") {
    name = "spigotmc-repo"
  }
}

dependencies {
  compileOnly("org.spigotmc:spigot-api:26.3-R0.1-SNAPSHOT")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
  implementation("com.squareup.okhttp3:okhttp:5.5.0")

  testImplementation("org.spigotmc:spigot-api:26.3-R0.1-SNAPSHOT")
  testImplementation(kotlin("test"))
  testImplementation(platform("org.junit:junit-bom:6.1.3"))
  testImplementation("org.junit.jupiter:junit-jupiter")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
  jvmToolchain(25)
}

tasks {
  test {
    useJUnitPlatform()
  }

  shadowJar {
    archiveClassifier = ""
    // Relocated so other plugins that ship their own Kotlin or OkHttp can't clash with ours.
    listOf("kotlin", "kotlinx", "okhttp3", "okio").forEach { relocate(it, "ru.mcbuddy.spigot.libs.$it") }
    mergeServiceFiles()
    exclude("META-INF/*.kotlin_module", "META-INF/versions/*/module-info.class", "module-info.class")
    dependencies { exclude(dependency("org.jetbrains:annotations")) }
  }

  // One jar with everything inside; a jar without Kotlin wouldn't load anyway.
  jar { enabled = false }

  assemble {
    dependsOn(shadowJar)
  }

  processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
      expand(props)
    }
  }
}
