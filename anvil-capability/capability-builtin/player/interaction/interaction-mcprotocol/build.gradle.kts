plugins {
	id("assembly")
	id("segmented")
}

description = "MCProtocolLib worker side of the built-in interaction capability, with one segment per release range"

dependencies {
	implementation(projects.anvilCapability.capabilityProtocolApi)
	implementation(projects.anvilCapability.capabilityBuiltin.player.interaction.interactionApi)

	// The family root embeds the common code into its shaded JAR, which every worker class path holds.
	compileOnly(projects.anvilCapability.capabilityBuiltin.player.interaction.interactionCommon)

	testImplementation(projects.anvilCapability.capabilityBuiltin.player.interaction.interactionCommon)
}
