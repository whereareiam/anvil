plugins {
	alias(libs.plugins.toolkit.architecture)
	alias(libs.plugins.toolkit.publish.maven)
	id("unit")
}

description = "Anvil tooling preparation and definition discovery for Gradle"
base.archivesName.set("gradle-tooling")

dependencies {
	implementation(projects.anvilIntegration.integrationGradle.gradleArtifacts)
	implementation(libs.jackson.databind)

	compileOnly(gradleApi())

	testImplementation(projects.anvilApi)
}

toolkitPublish {
	artifactId.set("gradle-tooling")
	pom {
		name.set("Anvil Project Tooling")
		description.set(project.description)
	}
}
