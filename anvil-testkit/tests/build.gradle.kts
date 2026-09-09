plugins {
	base
}

description = "Runtime integration and live platform verification suites"

tasks.named("build") {
	dependsOn(":anvil-testkit:tests:runtime:build", ":anvil-testkit:tests:server:build")
}
