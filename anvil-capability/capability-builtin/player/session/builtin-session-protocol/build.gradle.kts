plugins {
    alias(libs.plugins.toolkit.architecture)
    id("unit")
}

description = "MCProtocolLib adapter for the built-in session capability"

dependencies {
    implementation(libs.mcprotocol)

    compileOnly(projects.anvilApi)
    compileOnly(projects.anvilCapability.capabilityBuiltin.player.session.builtinSessionApi)
}
