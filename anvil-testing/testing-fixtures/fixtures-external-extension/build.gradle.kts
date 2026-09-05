plugins {
    id("unit")
}

description = "External backend, capabilities, and agent operations compiled against public APIs only"

dependencies {
    compileOnly(projects.anvilAgent.agentApi)
    compileOnly(projects.anvilApi)
    compileOnly(projects.anvilCapability.capabilityApi)
    compileOnly(projects.anvilCapability.capabilityBuiltin.movement.builtinMovementApi)
    compileOnly(projects.anvilCapability.capabilityBuiltin.session.builtinSessionApi)
    compileOnly(projects.anvilProtocol.protocolApi)
}
