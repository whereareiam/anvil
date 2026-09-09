plugins {
    alias(libs.plugins.toolkit.architecture)
    id("capability")
}

description = "Public wiring bundle for the built-in inventory capability"

dependencies {
    api(projects.anvilCapability.capabilityProtocolApi)
    api(projects.anvilCapability.capabilityBuiltin.player.inventory.builtinInventoryApi)

    compileOnly(projects.anvilCapability.capabilityBuiltin.player.inventory.builtinInventoryProtocol)
    compileOnly(libs.mcprotocol)

    embedded(projects.anvilCapability.capabilityBuiltin.player.inventory.builtinInventoryProtocol) { isTransitive = false }
}
