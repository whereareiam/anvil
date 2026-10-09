plugins {
	id("module-assembly")
	id("module-adapter")
}

description = "MCProtocolLib worker side of the built-in inventory capability, with one segment per release range"

dependencies {
	implementation(projects.anvilCapability.capabilityProtocolApi)
	implementation(projects.anvilCapability.capabilityBuiltin.player.inventory.inventoryApi)

	// The family root embeds the common code into its shaded JAR, which every worker class path holds.
	compileOnly(projects.anvilCapability.capabilityBuiltin.player.inventory.inventoryCommon)

	testImplementation(projects.anvilCapability.capabilityBuiltin.player.inventory.inventoryCommon)
}
