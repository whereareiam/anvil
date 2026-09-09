plugins {
	alias(libs.plugins.toolkit.architecture)
	id("unit")
}

description = "Platform selection, distribution preparation, and forwarding plans"

dependencies {
	compileOnly(projects.anvilApi)
	compileOnly(projects.anvilPlatform.platformApi)

	testImplementation(projects.anvilApi)
	testImplementation(projects.anvilPlatform.platformApi)
}
