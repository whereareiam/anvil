plugins {
	id("module-api")
}

description = "Release-neutral client port that CloudburstMC Protocol client segments implement for the Anvil worker"

dependencies {
	api(projects.anvilApi)
}
