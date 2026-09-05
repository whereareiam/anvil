plugins {
    alias(libs.plugins.toolkit.architecture)
    alias(libs.plugins.toolkit.publish.maven)
    id("unit")
}

description = "Native inventory packets for the MCProtocolLib 1206 API family"

dependencies {
    implementation(projects.anvilProtocol.protocolAdapterApi)

    compileOnly(libs.mcprotocol1206)
}
