plugins {
	alias(libs.plugins.toolkit.architecture)
	alias(libs.plugins.toolkit.publish.maven)
	id("unit")
}

architecture {
	kind = assembly
}

description = "Optional tooling actions and observations for Anvil's built-in capabilities"

toolkitPublish {
	artifactId.set("tooling-builtin")
}

dependencies {
	implementation(projects.anvilCapability.capabilityBuiltin.player.messages.builtinMessagesApi)
	implementation(projects.anvilCapability.capabilityBuiltin.player.session.builtinSessionApi)
	implementation(projects.anvilTooling.toolingExtensionApi)
}
