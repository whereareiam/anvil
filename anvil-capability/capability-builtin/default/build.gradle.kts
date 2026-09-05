plugins {
    alias(libs.plugins.toolkit.architecture)
    alias(libs.plugins.toolkit.publish.maven)
    id("unit")
}

description = "Default aggregate containing every built-in Anvil player capability"

dependencies {
    api(projects.anvilCapability.capabilityBuiltin.interaction.builtinInteraction)
    api(projects.anvilCapability.capabilityBuiltin.inventory.builtinInventory)
    api(projects.anvilCapability.capabilityBuiltin.messages.builtinMessages)
    api(projects.anvilCapability.capabilityBuiltin.movement.builtinMovement)
    api(projects.anvilCapability.capabilityBuiltin.server.builtinServer)
    api(projects.anvilCapability.capabilityBuiltin.session.builtinSession)
}
