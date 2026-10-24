plugins {
	id("module-java")
}

description = "Behaviour every workbench shares: checking a scenario's requirements against its facts"

dependencies {
	compileOnly(projects.anvilApi)
	compileOnly(projects.anvilWorkbench.workbenchApi)

	testImplementation(projects.anvilApi)
	testImplementation(projects.anvilWorkbench.workbenchApi)
}
