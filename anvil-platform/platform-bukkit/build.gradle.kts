plugins {
    base
}

tasks.named("build") {
    dependsOn(":anvil-platform:platform-bukkit:platform-bukkit-agent:build")
}
