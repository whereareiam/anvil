plugins {
	id("module-api")
}

// The protocol both ends of a workbench connection speak. Like the player worker kits it carries behaviour by
// design: the engine's client and the workbench's server embed the same framing, mapping and error translation.
description = "The protocol between an engine and a workbench on another machine"

dependencies {
	api(projects.anvilWorkbench.workbenchApi)
	api(libs.jackson.databind)

	implementation(libs.jackson.parameters)
}
