plugins {
    alias(libs.plugins.toolkit.architecture)
    id("capability")
}

description = "Public wiring bundle for the built-in session capability"

dependencies {
    api(projects.anvilCapability.capabilityBuiltin.session.builtinSessionApi)

    embedded(projects.anvilCapability.capabilityBuiltin.session.builtinSessionProtocol) { isTransitive = false }
}
