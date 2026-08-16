plugins {
    id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT"
}

val modVersion: String = project.property("modVersion")!! as String

version = "$modVersion+${libs.versions.minecraft.get()}"
group = "ua.bonfiremc"

base {
    archivesName = "slc"
}

repositories {
    maven("https://maven.nucleoid.xyz/")
}

@Suppress("AvoidDuplicateDependencies", "RedundantSuppression")
dependencies {
    minecraft(libs.minecraft)

    implementation(libs.fabric.loader)
    implementation(libs.fabric.api)

    implementation(libs.translations)
    include(libs.translations)
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks {
    processResources {
        val minecraftVersion: String = libs.versions.minecraft.get()

        inputs.property("version", version)
        inputs.property("minecraft_version", minecraftVersion)

        filesMatching("fabric.mod.json") {
            expand(
                "version" to version,
                "minecraft_version" to minecraftVersion
            )
        }
    }

    jar {
        from("LICENSE")
    }
}
