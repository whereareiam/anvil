plugins {
	id("org.jetbrains.kotlin.jvm")
	id("org.jetbrains.kotlin.plugin.lombok")
	id("module-java")
	id("test-intellij")
}

description = "Scenario catalog, environments, consoles, and run configurations for the IntelliJ integration"

dependencies {
	implementation(projects.anvilIntegration.integrationIntellij.intellijApi)
	implementation(projects.anvilTooling.toolingApi)
	implementation(libs.jackson.databind)

	testImplementation(projects.anvilIntegration.integrationIntellij.intellijCommon)
	testImplementation(testFixtures(projects.anvilIntegration.integrationIntellij.intellijCommon))
	testImplementation(projects.anvilTooling.toolingRunner)
}

// The tool window, the run configuration type, and scenario nodes share the brand mark.
tasks.processResources {
	from(rootProject.file("assets/branding/anvil-transparent.svg")) {
		into("icons")
		rename { "anvil.svg" }
	}
}
