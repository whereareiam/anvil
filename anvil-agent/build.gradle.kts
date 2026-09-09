plugins {
    base
}

tasks.named("build") {
    dependsOn(
        ":anvil-agent:agent-api:build",
        ":anvil-agent:agent-client:build",
        ":anvil-agent:agent-client:client-api:build",
        ":anvil-agent:agent-server:build",
        ":anvil-agent:agent-server:server-api:build"
    )
}
