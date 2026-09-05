plugins {
    alias(libs.plugins.toolkit.architecture)
    id("capability")
}

description = "Public wiring bundle for the built-in inventory capability"

dependencies {
    api(projects.anvilCapability.capabilityBuiltin.inventory.builtinInventoryApi)

    embedded(projects.anvilCapability.capabilityBuiltin.inventory.builtinInventoryProtocol) { isTransitive = false }
    embedded(projects.anvilCapability.capabilityBuiltin.session.builtinSession) { isTransitive = false }
}
