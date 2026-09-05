plugins {
    alias(libs.plugins.toolkit.architecture)
    id("capability")
}

description = "Public wiring bundle for the built-in movement capability"

dependencies {
    api(projects.anvilCapability.capabilityBuiltin.movement.builtinMovementApi)

    embedded(projects.anvilCapability.capabilityBuiltin.movement.builtinMovementProtocol) { isTransitive = false }
    embedded(projects.anvilCapability.capabilityBuiltin.session.builtinSession) { isTransitive = false }
}
