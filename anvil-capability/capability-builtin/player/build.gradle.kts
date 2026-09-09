plugins {
	base
}

tasks.named("build") {
	dependsOn(
		":anvil-capability:capability-builtin:player:interaction:builtin-interaction-api:build",
		":anvil-capability:capability-builtin:player:interaction:builtin-interaction-protocol:build",
		":anvil-capability:capability-builtin:player:interaction:builtin-interaction:build",
		":anvil-capability:capability-builtin:player:inventory:builtin-inventory-api:build",
		":anvil-capability:capability-builtin:player:inventory:builtin-inventory-protocol:build",
		":anvil-capability:capability-builtin:player:inventory:builtin-inventory:build",
		":anvil-capability:capability-builtin:player:messages:builtin-messages-api:build",
		":anvil-capability:capability-builtin:player:messages:builtin-messages-protocol:build",
		":anvil-capability:capability-builtin:player:messages:builtin-messages:build",
		":anvil-capability:capability-builtin:player:movement:builtin-movement-api:build",
		":anvil-capability:capability-builtin:player:movement:builtin-movement-protocol:build",
		":anvil-capability:capability-builtin:player:movement:builtin-movement:build",
		":anvil-capability:capability-builtin:player:server:builtin-server-api:build",
		":anvil-capability:capability-builtin:player:server:builtin-server-bridge:build",
		":anvil-capability:capability-builtin:player:server:builtin-server:build",
		":anvil-capability:capability-builtin:player:session:builtin-session-api:build",
		":anvil-capability:capability-builtin:player:session:builtin-session-protocol:build",
		":anvil-capability:capability-builtin:player:session:builtin-session:build"
	)
}
