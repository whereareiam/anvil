plugins {
    base
}

tasks.named("build") {
    dependsOn(
        ":anvil-platform:platform-velocity:platform-velocity-provider:build",
        ":anvil-platform:platform-velocity:platform-velocity-agent:build"
    )
}
