// The standalone harness build that runs the real vanilla client headless. It compiles against Mojang's clients,
// which generateVanillaData downloads into ~/.anvil/vanilla-clients, so it is never part of the root build or of
// Anvil's artifacts. Run it from the repository root with ./gradlew -p anvil-testkit/physics/harness.
pluginManagement {
    includeBuild("../../../build-logic")

    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://registry.whereareiam.me/maven/packages")
    }
}

rootProject.name = "anvil-physics-harness"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        mavenCentral()
        // The AutoRenamingTool, which remaps the obfuscated clients to Mojang's names.
        exclusiveContent {
            forRepository { maven("https://maven.neoforged.net/releases") }
            filter { includeModule("net.neoforged", "AutoRenamingTool") }
        }
    }

    versionCatalogs {
        create("libs") {
            from(files("../../../gradle/libs.versions.toml"))
        }
    }
}
