plugins {
    alias(libs.plugins.toolkit.architecture)
    id("unit")
}

description = "MCProtocolLib adapter for the built-in inventory capability"

dependencies {
    implementation(libs.mcprotocol)

    compileOnly(projects.anvilApi)
    compileOnly(projects.anvilCapability.capabilityBuiltin.player.inventory.builtinInventoryApi)
}
