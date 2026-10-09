plugins {
	id("jvm")
	id("publication")
}

description = "Filesystem cache entries and staged publication for Anvil environments"

toolkitPublish {
	artifactId.set("cache-filesystem")
}

dependencies {
	api(projects.anvilEnvironment.cache.cacheApi)

	testImplementation(projects.anvilEnvironment.cache.cacheApi)
}
