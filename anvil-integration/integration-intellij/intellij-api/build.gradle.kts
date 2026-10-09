plugins {
	id("jvm")
	id("intellij-platform")
}

description = "Contracts and immutable values for the IntelliJ integration"

// Contracts compile against the platform alone, without bundled IDE plugins.
intellijPlatformModule {
	bundledPlugins.empty()
}

dependencies {
	api(projects.anvilApi)
	api(projects.anvilTooling.toolingApi)
}
