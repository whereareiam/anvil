plugins {
	`kotlin-dsl`
	id("gradle-plugin")
	id("fixtures")
	id("fixture-repository")
}

description = "Gradle scenario tooling for Anvil"

dependencies {
	api(projects.anvilIntegration.integrationGradle.gradleDsl)

	implementation(projects.anvilIntegration.integrationGradle.gradleArtifacts)
	implementation(projects.anvilIntegration.integrationGradle.gradleTooling)

	testImplementation(projects.anvilProtocol.protocolApi)
	testImplementation(libs.jackson.databind)
}

gradlePlugin {
    plugins {
        create("anvilScenario") {
            id = "me.whereareiam.anvil"
            implementationClass = "me.whereareiam.anvil.integration.gradle.AnvilPlugin"
            displayName = "Anvil"
            description = "Installs foreground Anvil scenario tooling"
        }
    }
}

fixtures {
	process()
	toolingExtension()
}

toolkitPublish {
	artifactId.set("anvil-gradle-plugin")
}
