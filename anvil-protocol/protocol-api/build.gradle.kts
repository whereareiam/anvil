plugins {
    alias(libs.plugins.toolkit.architecture)
    alias(libs.plugins.toolkit.publish.maven)
    id("unit")
}

description = "Backend-neutral simulated-player protocol contracts for Anvil"

dependencies {
    api(projects.anvilApi)
}
