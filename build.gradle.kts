import dev.hygradle.dsl.plugin.LatePlugin
import dev.hygradle.dsl.run.Run

plugins {
    java
    id("dev.hygradle")
    id("com.gradleup.shadow") version "9.3.0"
}

group = property("group") as String
version = property("version") as String

repositories {
    mavenCentral()
    maven { name = "hytale-release"; url = uri("https://maven.hytale.com/release") }
    maven { name = "local-libs"; url = uri("libs") }
}

dependencies {
    implementation("me.elliesaur:HyUI:0.9.5")
    implementation("com.zaxxer:HikariCP:5.1.0")

    compileOnly("org.zuxaw:RPGLeveling:0.3.4")
    compileOnly("com.airijko:EndlessLeveling:7.6.0")
    compileOnly("com.orbisguard:OrbisGuard:0.8.8")

    compileOnly("org.xerial:sqlite-jdbc:3.45.1.0")
    compileOnly("com.mysql:mysql-connector-j:8.0.33")
    compileOnly("org.postgresql:postgresql:42.7.3")
    compileOnly("org.mariadb.jdbc:mariadb-java-client:3.3.3")
}

// === Hygradle Plugin + Run Configuration ===
hygradle {
    plugins {
        register<LatePlugin>("endgameqol") {
            manifest {
                mainClass = "endgame.plugin.EndgameQoL"
                name = "Endgame&QoL"
                group = "Config"
                version = project.version.toString()
                description = "Unlock the full potential of Hytale with Endgame & QoL! New bosses, items, mechanics and configuration system."
                includesAssetPack = true

                author {
                    name = "Lewai"
                }
                author {
                    name = "ReyZ41 (Models/Textures)"
                }

                dependency {
                    name = "NPC"
                    group = "Hytale"
                    version = "*"
                }
            }
        }
    }

    runs {
        register<Run>("dev")
    }
}

// === Compilation ===
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-deprecation", "-Xlint:all"))
}

// === Shadow JAR (shades HikariCP + SLF4J) ===
tasks.jar {
    enabled = false
}

tasks.shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles()
    relocate("com.zaxxer.hikari", "endgame.shaded.hikari")
    relocate("org.slf4j", "endgame.shaded.slf4j")
    from("LICENSE") { into("META-INF") }
}

// === Auto-deploy to Hytale Mods folder ===
tasks.register<Copy>("deploy") {
    dependsOn(tasks.shadowJar)
    from(tasks.shadowJar.get().archiveFile)
    into("C:/Users/Lewai/AppData/Roaming/Hytale/UserData/Mods")
}

tasks.build {
    dependsOn(tasks.shadowJar)
    finalizedBy("deploy")
}
