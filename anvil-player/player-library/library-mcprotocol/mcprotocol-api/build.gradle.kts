plugins {
	id("module-api")
}

description = "Release-neutral client port that MCProtocolLib client segments implement for the Anvil worker"

dependencies {
	api(projects.anvilApi)
}
