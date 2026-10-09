plugins {
	`java-test-fixtures`
	id("module-java")
	id("test-intellij")
}

description = "Lifecycle and tooling logic for the IntelliJ integration"

dependencies {
	api(projects.anvilIntegration.integrationIntellij.intellijApi)

	implementation(projects.anvilApi)
	implementation(libs.jackson.databind)

	compileOnly(projects.anvilTooling.toolingApi)

	testFixturesApi(libs.jackson.databind)
	testFixturesCompileOnly(libs.annotations)

	testImplementation(projects.anvilTooling.toolingApi)
	testImplementation(projects.anvilTooling.toolingRunner)
}
