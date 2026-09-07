plugins {
	alias(libs.plugins.toolkit.architecture)
	alias(libs.plugins.toolkit.publish.maven)
	id("unit")
}

dependencies {
	api(projects.anvilApi)

	api(projects.anvilProvisioning.provisioningApi)
	api(projects.anvilProvisioning.provisioningJava.api)
}
