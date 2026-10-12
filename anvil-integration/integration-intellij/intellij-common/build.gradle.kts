plugins {
	`java-test-fixtures`
	id("module-java")
	id("test-intellij")
}

description = "Standard implementations of the IntelliJ integration's shared services"

intellijPlatformModule {
	bundledPlugins.empty()
}

dependencies {
	implementation(projects.anvilIntegration.integrationIntellij.intellijApi)
	implementation(libs.jackson.databind)

	testFixturesImplementation(libs.jackson.databind)
	testFixturesImplementation(libs.junit.jupiter)
}

// Test fixtures drive Swing views and the platform's action system, so they compile against the same IDE.
configurations.testFixturesCompileOnly {
	extendsFrom(configurations.compileOnly.get())
}
