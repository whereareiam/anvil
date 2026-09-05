pluginManagement {
    val anvilVersion = providers.gradleProperty("anvilVersion").orElse("0.0.1").get()
    plugins {
        id("me.whereareiam.anvil") version anvilVersion
        id("me.whereareiam.anvil.platform.paper") version anvilVersion
    }

    repositories {
        mavenLocal()
        gradlePluginPortal()
        maven("https://registry.whereareiam.me/maven/packages")
        maven("https://repo.opencollab.dev/main/")
        maven("https://repo.opencollab.dev/maven-snapshots/")
    }
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
