plugins {
    id("unit")
}

description = "Server plugin and native agent operations used by live Anvil tests"

dependencies {
    compileOnly(projects.anvilAgent.agentApi)
    compileOnly(libs.paper)
}

tasks.processResources {
    expand("version" to project.version.toString())
}
