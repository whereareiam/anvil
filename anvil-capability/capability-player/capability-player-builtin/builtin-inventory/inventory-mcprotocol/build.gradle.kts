plugins {
	id("module-assembly")
	id("module-adapter")
}

description = "MCProtocolLib worker side of the built-in inventory capability, with one segment per release range"

dependencies {
	implementation(projects.anvilCapability.capabilityPlayer.capabilityPlayerApi)
	implementation(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinInventory.inventoryApi)

	// The family root embeds the common code into its shaded JAR, which every worker class path holds.
	compileOnly(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinInventory.inventoryCommon)

	testImplementation(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinInventory.inventoryCommon)
}
