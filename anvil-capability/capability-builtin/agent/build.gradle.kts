plugins {
	base
}

tasks.named("build") {
	dependsOn(
		":anvil-capability:capability-builtin:agent:console:builtin-console-api:build",
		":anvil-capability:capability-builtin:agent:console:builtin-console:build"
	)
}
