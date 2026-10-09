// Settings conventions load into the settings classloader, the parent of every project's, so this build stays
// lean: no project plugins and no third-party libraries that projects could request in other versions.
rootProject.name = "build-logic-settings"

pluginManagement {
	repositories {
		gradlePluginPortal()
		mavenCentral()
	}
}

dependencyResolutionManagement {
	repositories {
		gradlePluginPortal()
		mavenCentral()
	}

	versionCatalogs {
		create("libs") {
			from(files("../../gradle/libs.versions.toml"))
		}
	}
}
