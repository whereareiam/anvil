plugins {
    alias(libs.plugins.toolkit.architecture)
    id("capability")
}

description = "Public wiring bundle for the built-in interaction capability"

dependencies {
    api(projects.anvilCapability.capabilityBuiltin.interaction.builtinInteractionApi)

    embedded(projects.anvilCapability.capabilityBuiltin.interaction.builtinInteractionProtocol) { isTransitive = false }
    embedded(projects.anvilCapability.capabilityBuiltin.session.builtinSession) { isTransitive = false }
}
