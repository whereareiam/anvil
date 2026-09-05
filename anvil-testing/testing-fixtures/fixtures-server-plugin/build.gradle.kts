plugins {
    id("agent-java")
}

description = "Server plugin and native agent operations used by live Anvil tests"

dependencies {
    compileOnly(projects.anvilAgent.agentApi)
    compileOnly(libs.paperLegacy)
}

tasks.processResources {
    expand("version" to project.version.toString())
}
