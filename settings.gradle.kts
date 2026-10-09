import org.jetbrains.intellij.platform.gradle.extensions.intellijPlatform

pluginManagement {
    includeBuild("build-logic")

    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
        maven("https://registry.whereareiam.me/maven/packages")
        maven("https://repo.opencollab.dev/main/")
        maven("https://repo.opencollab.dev/maven-snapshots/")
    }

    plugins {
        // The server testkit applies the scenario plugin published for this build's own version.
        id("me.whereareiam.anvil") version providers.gradleProperty("anvilVersion").get()
    }
}

plugins {
    id("org.jetbrains.intellij.platform.settings") version "2.18.1"
    id("me.whereareiam.toolkit.project-discovery") version "dev-757d944"
}

rootProject.name = "Anvil"
// Consumer examples resolve the published plugin independently of the build that produces it.
rootProject.children.removeAll { it.name == "examples" }
project(":anvil-testkit").children.removeAll { it.name == "fixtures" }

includeBuild(".")

includeBuild("anvil-testkit/fixtures") {
    name = "anvil-test-fixtures"
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)

    repositories {
        intellijPlatform { defaultRepositories() }
        mavenLocal()
        mavenCentral()
        maven("https://registry.whereareiam.me/maven/packages")
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        maven("https://repo.opencollab.dev/main/")
        maven("https://repo.opencollab.dev/maven-snapshots/")
    }
}
