plugins {
	id("module-java")
}

description = "Library-neutral host provider and worker binding of the built-in inventory capability"

dependencies {
	compileOnly(projects.anvilApi)
	compileOnly(projects.anvilCapability.capabilityApi)
	compileOnly(projects.anvilCapability.capabilityProtocolApi)
	compileOnly(projects.anvilCapability.capabilityBuiltin.player.inventory.inventoryApi)

	testImplementation(projects.anvilApi)
	testImplementation(projects.anvilCapability.capabilityApi)
	testImplementation(projects.anvilCapability.capabilityProtocolApi)
	testImplementation(projects.anvilCapability.capabilityBuiltin.player.inventory.inventoryApi)
}
