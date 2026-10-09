plugins {
	id("module-java")
}

description = "Platform selection, distribution preparation, and forwarding plans"

dependencies {
	implementation(libs.jackson.databind)
	implementation(libs.jackson.toml)

	compileOnly(projects.anvilApi)
	compileOnly(projects.anvilPlatform.platformApi)

	testImplementation(projects.anvilApi)
	testImplementation(projects.anvilPlatform.platformApi)
}
