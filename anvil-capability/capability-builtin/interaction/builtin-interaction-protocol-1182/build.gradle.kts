plugins {
    alias(libs.plugins.toolkit.architecture)
    alias(libs.plugins.toolkit.publish.maven)
    id("unit")
}

description = "Native interaction packets for the MCProtocolLib 1182 API family"

dependencies {
    implementation(projects.anvilProtocol.protocolAdapterApi)

    compileOnly(libs.mcprotocol1182)
}
