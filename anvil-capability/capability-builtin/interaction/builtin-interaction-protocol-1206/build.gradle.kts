plugins {
    alias(libs.plugins.toolkit.architecture)
    alias(libs.plugins.toolkit.publish.maven)
    id("unit")
}

description = "Native interaction packets for the MCProtocolLib 1206 API family"

dependencies {
    implementation(projects.anvilProtocol.protocolAdapterApi)

    compileOnly(libs.mcprotocol1206)
}
