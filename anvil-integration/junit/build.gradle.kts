plugins {
    base
}

tasks.named("build") {
    dependsOn(
        ":anvil-integration:junit:extension:build",
        ":anvil-integration:junit:gradle:build",
    )
}
