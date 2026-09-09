plugins {
    alias(libs.plugins.toolkit.architecture)
    alias(libs.plugins.toolkit.publish.maven)
    id("unit")
}

description = "Default aggregate containing built-in player and agent capabilities"

dependencies {
    api(projects.anvilCapability.capabilityBuiltin.agent.console.builtinConsole)
    api(projects.anvilCapability.capabilityBuiltin.player.interaction.builtinInteraction)
    api(projects.anvilCapability.capabilityBuiltin.player.inventory.builtinInventory)
    api(projects.anvilCapability.capabilityBuiltin.player.messages.builtinMessages)
    api(projects.anvilCapability.capabilityBuiltin.player.movement.builtinMovement)
    api(projects.anvilCapability.capabilityBuiltin.player.server.builtinServer)
    api(projects.anvilCapability.capabilityBuiltin.player.session.builtinSession)
}
