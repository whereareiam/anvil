plugins {
    base
}

tasks.named("build") {
    dependsOn(
        ":anvil-capability:capability-api:build",
        ":anvil-capability:capability-runtime:build",
        ":anvil-capability:capability-builtin:build"
    )
}
