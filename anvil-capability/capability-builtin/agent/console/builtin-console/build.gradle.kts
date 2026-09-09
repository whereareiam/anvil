plugins {
	alias(libs.plugins.toolkit.architecture)
	id("capability")
}

description = "Agent wiring bundle for the built-in console capability"

dependencies {
	api(projects.anvilCapability.capabilityAgentApi)
	api(projects.anvilCapability.capabilityBuiltin.agent.console.builtinConsoleApi)

	implementation(projects.anvilAgent.agentApi)
}
