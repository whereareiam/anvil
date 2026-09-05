plugins {
	`kotlin-dsl`
	alias(libs.plugins.toolkit.architecture)
	id("gradle-plugin")
}

description = "Gradle adapter for Anvil JUnit integration"

dependencies {
	implementation(projects.anvilTooling.gradle.scenarios)

	compileOnly(projects.anvilEngine)

	testImplementation(gradleTestKit())
}

gradlePlugin {
    plugins {
        create("anvilJunit") {
            id = "me.whereareiam.anvil.junit"
            implementationClass = "me.whereareiam.anvil.junit.gradle.AnvilJunitPlugin"
            displayName = "Anvil JUnit"
            description = "Installs automated Anvil JUnit scenarios"
        }
    }
}
