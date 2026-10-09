plugins {
	id("module-capability")
}

architecture {
	sharedApis = setOf(projects.anvilCapability.capabilityApi.path)
}

description = "Public wiring bundle for the built-in server capability"

dependencies {
	api(projects.anvilCapability.capabilityApi)
}
