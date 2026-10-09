plugins {
	`kotlin-dsl`
	id("gradle-plugin")
}

description = "Gradle adapter for Anvil JUnit integration"

dependencies {
	api(projects.anvilIntegration.integrationGradle.gradleDsl)
}

gradlePlugin {
    plugins {
        create("anvilJunit") {
            id = "me.whereareiam.anvil.junit"
            implementationClass = "me.whereareiam.anvil.integration.junit.gradle.AnvilJunitPlugin"
            displayName = "Anvil JUnit"
            description = "Installs automated Anvil JUnit scenarios"
        }
    }
}
