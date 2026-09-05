plugins {
    alias(libs.plugins.toolkit.architecture)
    alias(libs.plugins.toolkit.publish.maven)
    id("unit")
}

description = "Native messages packets for the MCProtocolLib 1182 API family"

dependencies {
    implementation(projects.anvilProtocol.protocolAdapterApi)
    implementation(libs.adventure.plain)

    compileOnly(libs.mcprotocol1182)
}
