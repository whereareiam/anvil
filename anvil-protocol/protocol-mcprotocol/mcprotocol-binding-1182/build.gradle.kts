plugins {
    alias(libs.plugins.toolkit.architecture)
    alias(libs.plugins.toolkit.publish.maven)
    id("unit")
}

description = "Native MCProtocolLib 1182 packet API binding"

dependencies {
    implementation(projects.anvilProtocol.protocolAdapterApi)
    implementation(libs.adventure.plain)

    compileOnly(libs.mcprotocol1182)
}
