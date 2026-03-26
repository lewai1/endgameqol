plugins {
    java
    id("com.gradleup.shadow") version "9.3.0"
}

group = property("group") as String
version = property("version") as String

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    maven { name = "hytale-release"; url = uri("https://maven.hytale.com/release") }
    maven { name = "hytale-pre-release"; url = uri("https://maven.hytale.com/pre-release") }
    maven { name = "local-libs"; url = uri("libs") }
}

dependencies {
    compileOnly("com.hypixel.hytale:Server:2026.03.25-89796e57b")

    implementation("me.elliesaur:HyUI:0.9.4")
    implementation("com.zaxxer:HikariCP:5.1.0")

    compileOnly("org.zuxaw:RPGLeveling:0.3.0")
    compileOnly("com.airijko:EndlessLeveling:6.9-BETA-6")
    compileOnly("com.orbisguard:OrbisGuard:0.8.7")
    // SimpleClaims — optional claim protection support for HyRifts portal placement
    // Drop SimpleClaims-*.jar into the libs/ folder to compile with claim support.
    compileOnly(fileTree("libs") { include("SimpleClaims*.jar") })

    compileOnly("org.xerial:sqlite-jdbc:3.45.1.0")
    compileOnly("com.mysql:mysql-connector-j:8.0.33")
    compileOnly("org.postgresql:postgresql:42.7.3")
    compileOnly("org.mariadb.jdbc:mariadb-java-client:3.3.3")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-deprecation", "-Xlint:all"))
}

tasks.jar {
    enabled = false
}

tasks.shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles()
    relocate("com.zaxxer.hikari", "endgame.shaded.hikari")
}

// Auto-deploy to Hytale Mods folder and test server after build
tasks.register<Copy>("deploy") {
    dependsOn(tasks.shadowJar)
    from(tasks.shadowJar.get().archiveFile)
    into("C:/Users/hazem/AppData/Roaming/Hytale/UserData/Mods")
}

tasks.register<Copy>("deployServer") {
    dependsOn(tasks.shadowJar)
    from(tasks.shadowJar.get().archiveFile)
    into("D:/HytaleModding/run/mods")
}

tasks.build {
    dependsOn(tasks.shadowJar)
    finalizedBy("deploy", "deployServer")
}
