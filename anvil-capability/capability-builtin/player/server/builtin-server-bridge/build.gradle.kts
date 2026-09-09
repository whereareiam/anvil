plugins {
    alias(libs.plugins.toolkit.architecture)
    id("unit")
}

description = "Agent adapter for the built-in server capability"

dependencies {
    compileOnly(projects.anvilApi)
    compileOnly(projects.anvilCapability.capabilityBuiltin.player.server.builtinServerApi)
}
