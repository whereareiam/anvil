plugins {
    alias(libs.plugins.toolkit.architecture)
    alias(libs.plugins.toolkit.publish.maven)
    id("unit")
}

description = "Shared host and worker contracts for capability-owned protocol adapters"

dependencies {
    api(projects.anvilCapability.capabilityApi)
    api(libs.jackson.databind)
}
