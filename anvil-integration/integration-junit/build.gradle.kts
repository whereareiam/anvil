plugins {
	id("module-assembly")
}

description = "JUnit extension integration for Anvil scenarios"

toolkitPublish {
	artifactId.set("junit")
}

dependencies {
	api(projects.anvilApi)
	api(libs.junit.jupiter)

	implementation(projects.anvilLauncher)

	runtimeOnly(libs.junit.platform)
}
