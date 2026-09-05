plugins {
	`kotlin-dsl`
	alias(libs.plugins.toolkit.architecture)
	id("gradle-plugin")
}

description = "Gradle scenario tooling for Anvil"

dependencies {
	implementation(projects.anvilProtocol.protocolApi)
	implementation(projects.anvilTooling.toolingRunner)

	compileOnly(projects.anvilLauncher)

	testImplementation(gradleTestKit())
}

gradlePlugin {
    plugins {
        create("anvilScenario") {
            id = "me.whereareiam.anvil.scenarios"
            implementationClass = "me.whereareiam.anvil.gradle.AnvilScenariosPlugin"
            displayName = "Anvil Scenarios"
            description = "Installs foreground Anvil scenario tooling"
        }
    }
}
