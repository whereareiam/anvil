plugins {
	`java-library`
	id("org.jetbrains.intellij.platform")
	alias(libs.plugins.toolkit.architecture)
	id("unit")
}

architecture {
	kind = api
}

description = "Contracts and immutable values for the IntelliJ integration"

dependencies {
	api(projects.anvilApi)
	api(projects.anvilTooling.toolingApi)

	intellijPlatform {
		create("IC", libs.versions.intellij.idea)
	}
}
