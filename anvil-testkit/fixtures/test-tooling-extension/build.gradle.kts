plugins {
	id("jvm")
}

description = "Independent custom capability and tooling extension using public Anvil contracts"

dependencies {
	compileOnly(anvil.api)
	compileOnly(anvil.capability.api)
	compileOnly(anvil.platform.api)
	compileOnly(anvil.protocol.api)
	compileOnly(anvil.tooling.extension.api)
}
