plugins {
	id("jvm")
	id("intellij-platform-tests")
}

description = "Gradle adapter for the IntelliJ integration"

intellijPlatformModule {
	bundledPlugins.add("org.jetbrains.plugins.gradle")
}

dependencies {
	implementation(projects.anvilIntegration.integrationIntellij.intellijApi)
}
