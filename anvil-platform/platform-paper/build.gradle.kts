plugins {
    base
}

tasks.named("build") {
    dependsOn(":anvil-platform:platform-paper:platform-paper-provider:build")
}
