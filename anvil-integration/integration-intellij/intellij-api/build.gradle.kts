plugins {
	id("module-java")
	id("module-intellij")
}

description = "Contracts shared by the IntelliJ integration's features and build integrations"

// Contracts compile against the platform alone, without bundled IDE plugins.
intellijPlatformModule {
	bundledPlugins.empty()
}
