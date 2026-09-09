plugins {
    alias(libs.plugins.toolkit.architecture)
    id("unit")
}

description = "MCProtocolLib adapter for the built-in movement capability"

dependencies {
    implementation(libs.mcprotocol)

    compileOnly(projects.anvilApi)
    compileOnly(projects.anvilCapability.capabilityBuiltin.player.movement.builtinMovementApi)
}
