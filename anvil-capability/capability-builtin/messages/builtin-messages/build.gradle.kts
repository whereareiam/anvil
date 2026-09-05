plugins {
    alias(libs.plugins.toolkit.architecture)
    id("capability")
}

description = "Public wiring bundle for the built-in messages capability"

dependencies {
    api(projects.anvilCapability.capabilityBuiltin.messages.builtinMessagesApi)

    embedded(projects.anvilCapability.capabilityBuiltin.messages.builtinMessagesProtocol) { isTransitive = false }
    embedded(projects.anvilCapability.capabilityBuiltin.session.builtinSession) { isTransitive = false }
}
