plugins {
    alias(libs.plugins.toolkit.architecture)
    alias(libs.plugins.toolkit.publish.maven)
    id("unit")
}

architecture {
    kind = assembly
}

description = "Version-isolated MCProtocolLib clients and native packet bindings"

dependencies {
    api(projects.anvilProtocol.protocolApi)

    runtimeOnly(projects.anvilProtocol.protocolMcprotocol.mcprotocolBinding1182)
    runtimeOnly(projects.anvilProtocol.protocolMcprotocol.mcprotocolBinding1206)
    runtimeOnly(projects.anvilProtocol.protocolMcprotocol.mcprotocolBinding2026)
    runtimeOnly(projects.anvilProtocol.protocolMcprotocol.mcprotocolClient)
}
