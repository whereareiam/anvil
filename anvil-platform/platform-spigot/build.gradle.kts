plugins {
    base
}

tasks.named("build") {
    dependsOn(":anvil-platform:platform-spigot:platform-spigot-provider:build")
}
