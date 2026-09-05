plugins {
    base
}

tasks.named("build") {
    dependsOn(
        ":anvil-protocol:protocol-api:build",
        ":anvil-protocol:protocol-adapter-api:build",
        ":anvil-protocol:protocol-mcprotocol:build"
    )
}
