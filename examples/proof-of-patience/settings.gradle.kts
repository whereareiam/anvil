import java.util.Properties

pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        maven("https://registry.whereareiam.me/maven/packages")
        maven("https://repo.opencollab.dev/main/")
        maven("https://repo.opencollab.dev/maven-snapshots/")
    }
}

val checkoutVersion = providers.fileContents(layout.settingsDirectory.file("../../gradle.properties"))
    .asText.map { text ->
        Properties().apply { load(text.reader()) }.getProperty("anvilVersion")
    }

val anvilVersion = providers.environmentVariable("VERSION")
    .orElse(providers.gradleProperty("anvilVersion"))
    .orElse(checkoutVersion)
    .get()

pluginManagement.resolutionStrategy.eachPlugin {
    if (requested.id.id == "me.whereareiam.anvil" || requested.id.id.startsWith("me.whereareiam.anvil."))
        useVersion(anvilVersion)
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../../gradle/libs.versions.toml"))
        }
    }

    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)

    repositories {
        mavenLocal()
        mavenCentral()
        maven("https://registry.whereareiam.me/maven/packages")
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.opencollab.dev/main/")
        maven("https://repo.opencollab.dev/maven-snapshots/")
    }
}

rootProject.name = "proof-of-patience"
