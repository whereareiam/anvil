plugins {
	alias(libs.plugins.toolkit.architecture)
	// Keep the publication plugin version visible to the nested API project.
	alias(libs.plugins.toolkit.publish.maven) apply false
	id("unit")
}

dependencies {
	implementation(libs.commons.compress)
	implementation(libs.jackson.databind)

	compileOnly(projects.anvilProvisioning.provisioningApi)
	compileOnly(projects.anvilProvisioning.provisioningJava.api)

	testImplementation(projects.anvilProvisioning.provisioningApi)
	testImplementation(projects.anvilProvisioning.provisioningJava.api)
	testImplementation(projects.anvilProvisioning.provisioningCache)
}
