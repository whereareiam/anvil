plugins {
	id("module-java")
}

description = "External in-process protocol library speaking the Java Edition protocol through the public worker kits"

dependencies {
	compileOnly(anvil.api)
	compileOnly(anvil.player.worker)
	compileOnly(anvil.player.api)
	compileOnly(anvil.edition.java.protocol.api)
	compileOnly(anvil.edition.java.protocol.worker)
}
