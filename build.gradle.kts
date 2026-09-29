plugins {
    java
    id("com.gradleup.shadow") version "9.1.0"
}

group = "com.valaryrpg"
version = "1.0.0"
description = "Instanced Mythic arena for ValaryRPG"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://mvn.lumine.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://jitpack.io")
    maven("https://repo.infernalsuite.com/repository/maven-snapshots/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("io.lumine:Mythic-Dist:5.10.0")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("com.github.NEZNAMY:TAB-API:5.2.5")
    compileOnly("com.infernalsuite.asp:api:4.2.0-SNAPSHOT")

    implementation("com.zaxxer:HikariCP:6.2.1")
    implementation("org.xerial:sqlite-jdbc:3.47.2.0")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.3")
}

tasks {
    processResources {
        val props = mapOf(
            "version" to version,
            "description" to project.description
        )
        filesMatching("plugin.yml") {
            expand(props)
        }
        filteringCharset = "UTF-8"
    }

    compileJava {
        options.encoding = "UTF-8"
        options.release.set(25)
    }

    shadowJar {
        archiveClassifier.set("")
        archiveBaseName.set("ValaryRPG-Arena")
        relocate("com.zaxxer.hikari", "com.valaryrpg.arena.libs.hikari")
        mergeServiceFiles()
        exclude("org/sqlite/native/Linux-Android/**")
        exclude("org/sqlite/native/Linux-Musl/**")
        exclude("org/sqlite/native/Linux/arm*/**")
        exclude("org/sqlite/native/Linux/aarch64/**")
        exclude("org/sqlite/native/Linux/ppc64/**")
        exclude("org/sqlite/native/Linux/riscv64/**")
        exclude("org/sqlite/native/Mac/**")
        exclude("org/sqlite/native/FreeBSD/**")
        exclude("org/sqlite/native/Windows/x86/**")
        exclude("org/sqlite/native/Windows/armv7/**")
        exclude("org/sqlite/native/Windows/aarch64/**")
        minimize {
            exclude(dependency("org.xerial:sqlite-jdbc:.*"))
            exclude(dependency("org.mariadb.jdbc:mariadb-java-client:.*"))
        }
    }

    jar {
        enabled = false
    }

    assemble {
        dependsOn(shadowJar)
    }
}
