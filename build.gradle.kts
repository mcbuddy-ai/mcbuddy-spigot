plugins {
  kotlin("jvm") version "2.2.21"
  kotlin("plugin.serialization") version "2.2.20"
  id("com.gradleup.shadow") version "9.2.2"
  id("xyz.jpenilla.run-paper") version "3.0.2"
}

group = "ru.mcbuddy"
version = "1.4.2"

repositories {
  mavenCentral()
  maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") {
    name = "spigotmc-repo"
  }
  maven("https://oss.sonatype.org/content/groups/public/") {
    name = "sonatype"
  }
}

dependencies {
  compileOnly("org.spigotmc:spigot-api:1.21.10-R0.1-SNAPSHOT")
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
  implementation("com.squareup.okhttp3:okhttp:5.2.1")
}

tasks {
  runServer {
    // Configure the Minecraft version for our task.
    // This is the only required configuration besides applying the plugin.
    // Your plugin's jar (or shadowJar if present) will be used automatically.
    minecraftVersion("1.21")
  }
}

val targetJavaVersion = 21
kotlin {
  jvmToolchain(targetJavaVersion)
}

tasks.build {
  dependsOn("shadowJar")
}

tasks.processResources {
  val props = mapOf("version" to version)
  inputs.properties(props)
  filteringCharset = "UTF-8"
  filesMatching("plugin.yml") {
    expand(props)
  }
}
