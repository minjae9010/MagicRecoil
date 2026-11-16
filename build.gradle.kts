import java.net.URI
import java.security.MessageDigest

plugins {
    java
}

group = "dev.magicrecoil"
description = "Camera recoil for MagicSpells on Paper"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(providers.gradleProperty("compilerJava").getOrElse("21").toInt()))
    withSourcesJar()
}

// Compile against the oldest supported API. Overrides exercise newer upstream APIs in CI.
val paperVersion = providers.gradleProperty("paperVersion").getOrElse("1.21.4-R0.1-SNAPSHOT")
val magicSpellsVersion = providers.gradleProperty("magicSpellsVersion").getOrElse("4.0-Beta-17")
val magicSpellsChecksums = mapOf(
    "4.0-Beta-17" to "ab085d1752c25eba0a933403503f456661cdb917fa493fe62002662e1a14fd61",
    "4.0-Beta-18" to "3eee4d06cb90088f38ed27b5dc1af4b381eb0eeb74ba0b6c14a4454e901b9fce",
    "4.0-Beta-19" to "5221afe397b4095edb6f0ecda8d9cece4a0a0bf8741a58d92f42858e847f343a"
)
val magicSpellsChecksum = providers.gradleProperty("magicSpellsSha256").orNull
    ?: magicSpellsChecksums[magicSpellsVersion]
    ?: error("Supply -PmagicSpellsSha256 for an unpinned MagicSpells build")
val magicSpellsJar = layout.buildDirectory.file("dependencies/MagicSpells-$magicSpellsVersion.jar")
val downloadMagicSpells by tasks.registering {
    val destination = magicSpellsJar
    inputs.property("version", magicSpellsVersion)
    inputs.property("sha256", magicSpellsChecksum)
    outputs.file(destination)
    doLast {
        val file = destination.get().asFile
        file.parentFile.mkdirs()
        val temporary = file.resolveSibling(file.name + ".part")
        val connection = URI("https://github.com/TheComputerGeek2/MagicSpells/releases/download/$magicSpellsVersion/MagicSpells-$magicSpellsVersion.jar").toURL().openConnection()
        connection.connectTimeout = 30_000
        connection.readTimeout = 60_000
        try {
            connection.getInputStream().use { input -> temporary.outputStream().use { input.copyTo(it) } }
            val digest = MessageDigest.getInstance("SHA-256").digest(temporary.readBytes()).joinToString("") { "%02x".format(it) }
            check(digest == magicSpellsChecksum) {
                "MagicSpells release checksum mismatch: $digest"
            }
            temporary.copyTo(file, overwrite = true)
        } finally {
            temporary.delete()
        }
    }
}
val magicSpells = files(magicSpellsJar).builtBy(downloadMagicSpells)

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperVersion")
    compileOnly(magicSpells)
    testImplementation(magicSpells)
    testImplementation("io.papermc.paper:paper-api:$paperVersion")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.24.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}
tasks.test { useJUnitPlatform() }
tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("plugin.yml") { expand("version" to project.version) }
}
tasks.jar {
    manifest.attributes["paperweight-mappings-namespace"] = "mojang"
    from("LICENSE")
}
tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
tasks.wrapper {
    gradleVersion = "9.3.0"
    distributionType = Wrapper.DistributionType.BIN
    distributionSha256Sum = "0d585f69da091fc5b2beced877feab55a3064d43b8a1d46aeb07996b0915e0e0"
}

// Optional diagnostics for an isolated test server. Never packaged in the addon.
val runtimeProbe = sourceSets.create("runtimeProbe") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
}
dependencies.add(runtimeProbe.compileOnlyConfigurationName, "net.dmulloy2:ProtocolLib:5.4.0")
tasks.register<Jar>("runtimeProbeJar") {
    group = "verification"
    description = "Build the optional, test-server-only protocol/trajectory probe."
    from(runtimeProbe.output)
    archiveBaseName.set("MagicRecoilProbe")
    destinationDirectory.set(layout.buildDirectory.dir("runtime-probe"))
    manifest.attributes["paperweight-mappings-namespace"] = "mojang"
}
