plugins {
	id("module-java")
	id("test-intellij")
}

description = "Gradle adapter for the IntelliJ integration"

intellijPlatformModule {
	bundledPlugins.add("org.jetbrains.plugins.gradle")
}

dependencies {
	implementation(projects.anvilIntegration.integrationIntellij.intellijApi)
}
