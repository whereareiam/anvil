plugins {
	base
}

description = "Internal test support, fixture applications, and cross-module verification"

tasks.named("build") {
	dependsOn(":anvil-testkit:support:build", ":anvil-testkit:tests:build")
}
