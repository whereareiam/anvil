plugins {
	alias(libs.plugins.toolkit.architecture)
	id("unit")
}

dependencies {
	compileOnly(projects.anvilApi)
	compileOnly(projects.anvilProvisioning.provisioningApi)

	testImplementation(projects.anvilApi)
	testImplementation(projects.anvilProvisioning.provisioningApi)
}
