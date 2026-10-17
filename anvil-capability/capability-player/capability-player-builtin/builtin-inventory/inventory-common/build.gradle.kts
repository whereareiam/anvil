plugins {
	id("module-java")
}

description = "Library-neutral host provider and worker binding of the built-in inventory capability"

dependencies {
	compileOnly(projects.anvilApi)
	compileOnly(projects.anvilCapability.capabilityApi)
	compileOnly(projects.anvilCapability.capabilityPlayer.capabilityPlayerApi)
	compileOnly(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinInventory.inventoryApi)
	compileOnly(projects.anvilPlayer.playerApi)

	testImplementation(projects.anvilApi)
	testImplementation(projects.anvilCapability.capabilityApi)
	testImplementation(projects.anvilCapability.capabilityPlayer.capabilityPlayerApi)
	testImplementation(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinInventory.inventoryApi)
	testImplementation(projects.anvilPlayer.playerApi)
}
