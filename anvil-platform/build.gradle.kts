plugins {
    base
}

tasks.named("build") {
    dependsOn(
        ":anvil-platform:platform-api:build",
        ":anvil-platform:platform-bukkit:platform-bukkit-agent:build",
        ":anvil-platform:platform-paper:platform-paper-provider:build",
        ":anvil-platform:platform-spigot:platform-spigot-provider:build",
        ":anvil-platform:platform-velocity:platform-velocity-provider:build",
        ":anvil-platform:platform-velocity:platform-velocity-agent:build",
        ":anvil-platform:platform-bungeecord:platform-bungeecord-provider:build",
        ":anvil-platform:platform-bungeecord:platform-bungeecord-agent:build"
    )
}
