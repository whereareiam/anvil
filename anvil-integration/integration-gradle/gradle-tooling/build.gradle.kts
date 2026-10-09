plugins {
	id("module-java")
	id("packaging-publication")
}

description = "Anvil tooling preparation and definition discovery for Gradle"

toolkitPublish {
	pom {
		name.set("Anvil Project Tooling")
	}
}

dependencies {
	implementation(projects.anvilIntegration.integrationGradle.gradleArtifacts)
	implementation(libs.jackson.databind)

	compileOnly(gradleApi())

	testImplementation(projects.anvilApi)
}
