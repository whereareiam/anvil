plugins {
	id("jvm")
}

description = "Library-neutral host provider and worker binding of the built-in interaction capability"

dependencies {
	compileOnly(projects.anvilApi)
	compileOnly(projects.anvilCapability.capabilityApi)
	compileOnly(projects.anvilCapability.capabilityProtocolApi)
	compileOnly(projects.anvilCapability.capabilityBuiltin.player.interaction.interactionApi)

	testImplementation(projects.anvilApi)
	testImplementation(projects.anvilCapability.capabilityApi)
	testImplementation(projects.anvilCapability.capabilityProtocolApi)
	testImplementation(projects.anvilCapability.capabilityBuiltin.player.interaction.interactionApi)
}
