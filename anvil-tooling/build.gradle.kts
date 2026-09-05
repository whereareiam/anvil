plugins {
    base
}

tasks.named("build") {
    dependsOn(
        ":anvil-tooling:tooling-runner:build",
        ":anvil-tooling:gradle:build"
    )
}
