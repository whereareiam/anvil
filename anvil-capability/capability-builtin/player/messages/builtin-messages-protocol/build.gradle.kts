plugins {
    alias(libs.plugins.toolkit.architecture)
    id("unit")
}

description = "MCProtocolLib adapter for the built-in messages capability"

dependencies {
    implementation(libs.adventure.plain)
    implementation(libs.mcprotocol)

    compileOnly(projects.anvilApi)
    compileOnly(projects.anvilCapability.capabilityBuiltin.player.messages.builtinMessagesApi)
}
