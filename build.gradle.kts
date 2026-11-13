import java.net.URI
import java.security.MessageDigest

plugins {
    java
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.19"
}

group = "dev.magicrecoil"
description = "Relative camera recoil for MagicSpells on Paper 1.21.11"

repositories { mavenCentral() }

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
}

// Compile against the exact upstream release, without bundling or modifying it.
val magicSpellsJar = layout.buildDirectory.file("dependencies/MagicSpells-4.0-Beta-18.jar")
val downloadMagicSpells by tasks.registering {
    val destination = magicSpellsJar
    outputs.file(destination)
    doLast {
        val file = destination.get().asFile
        file.parentFile.mkdirs()
        val temporary = file.resolveSibling(file.name + ".part")
        val connection = URI("https://github.com/TheComputerGeek2/MagicSpells/releases/download/4.0-Beta-18/MagicSpells-4.0-Beta-18.jar").toURL().openConnection()
        connection.connectTimeout = 30_000
        connection.readTimeout = 60_000
        try {
            connection.getInputStream().use { input -> temporary.outputStream().use { input.copyTo(it) } }
            val digest = MessageDigest.getInstance("SHA-256").digest(temporary.readBytes()).joinToString("") { "%02x".format(it) }
            check(digest == "3eee4d06cb90088f38ed27b5dc1af4b381eb0eeb74ba0b6c14a4454e901b9fce") {
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
    paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")
    compileOnly(magicSpells)
    testImplementation(magicSpells)
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.15.2")
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
