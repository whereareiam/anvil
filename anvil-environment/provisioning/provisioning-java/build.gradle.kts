plugins {
	id("jvm")
}

dependencies {
	implementation(libs.commons.compress)
	implementation(libs.jackson.databind)

	compileOnly(projects.anvilApi)
	compileOnly(projects.anvilEnvironment.provisioning.provisioningJava.javaApi)

	testImplementation(projects.anvilApi)
	testImplementation(projects.anvilEnvironment.cache)
	testImplementation(projects.anvilEnvironment.cache.cacheApi)
	testImplementation(projects.anvilEnvironment.provisioning.provisioningJava.javaApi)
}
