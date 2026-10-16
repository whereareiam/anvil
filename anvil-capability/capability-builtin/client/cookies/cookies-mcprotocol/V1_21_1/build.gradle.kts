plugins {
	id("module-adapter-segment")
}

dependencies {
	implementation(projects.anvilCapability.capabilityBuiltin.client.cookies.cookiesApi)
}
