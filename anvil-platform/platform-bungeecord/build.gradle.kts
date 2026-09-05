plugins {
    base
}

tasks.named("build") {
    dependsOn(
        ":anvil-platform:platform-bungeecord:platform-bungeecord-provider:build",
        ":anvil-platform:platform-bungeecord:platform-bungeecord-agent:build"
    )
}
