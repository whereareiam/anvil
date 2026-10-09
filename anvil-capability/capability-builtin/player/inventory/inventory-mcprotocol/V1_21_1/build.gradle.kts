plugins {
	id("module-adapter-segment")
}

dependencies {
	implementation(projects.anvilCapability.capabilityBuiltin.player.inventory.inventoryApi)
}
