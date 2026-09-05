plugins {
    base
}

tasks.named("build") {
    dependsOn(
        ":anvil-tooling:gradle:scenarios:build",
        ":anvil-tooling:gradle:bundle:build",
    )
}
