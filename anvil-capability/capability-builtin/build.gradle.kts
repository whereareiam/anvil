plugins {
	base
}

tasks.named("build") {
	dependsOn(
		":anvil-capability:capability-builtin:agent:build",
		":anvil-capability:capability-builtin:default:build",
		":anvil-capability:capability-builtin:player:build"
	)
}
