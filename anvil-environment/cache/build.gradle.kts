plugins {
	alias(libs.plugins.toolkit.architecture)
	alias(libs.plugins.toolkit.publish.maven)
	id("unit")
}

description = "Filesystem cache entries and staged publication for Anvil environments"

toolkitPublish {
	artifactId.set("cache-filesystem")
}

dependencies {
	api(projects.anvilEnvironment.cache.cacheApi)

	testImplementation(projects.anvilEnvironment.cache.cacheApi)
}
