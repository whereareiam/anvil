plugins {
    base
}

tasks.named("build") {
    dependsOn(
        ":anvil-agent:agent-api:build",
        ":anvil-agent:agent-common:build",
        ":anvil-agent:agent:build"
    )
}
