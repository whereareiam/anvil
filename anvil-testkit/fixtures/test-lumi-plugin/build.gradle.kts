plugins {
	id("module-java")
	id("packaging-version-stamp")
}

description = "Lumi plugin reporting what live Anvil tests of Bedrock players observe on the server"

dependencies {
	compileOnly(libs.lumi)
}
