plugins {
	id("module-java")
}

description = "A workbench on another machine, reached from the engine over TLS"

dependencies {
	implementation(projects.anvilWorkbench.workbenchProtocol)
	implementation(libs.jackson.databind)

	compileOnly(projects.anvilApi)
	compileOnly(projects.anvilWorkbench.workbenchApi)

	testImplementation(projects.anvilApi)
	testImplementation(projects.anvilWorkbench.workbenchApi)
}
