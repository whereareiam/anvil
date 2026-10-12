plugins {
	id("org.jetbrains.kotlin.jvm")
	id("org.jetbrains.kotlin.plugin.lombok")
	id("module-java")
	id("test-intellij")
}

description = "Project and global accounts, account pools, and browser sign-in for the IntelliJ integration"

intellijPlatformModule {
	bundledPlugins.empty()
}

dependencies {
	implementation(projects.anvilApi)
	implementation(projects.anvilIntegration.integrationIntellij.intellijApi)

	testImplementation(projects.anvilIntegration.integrationIntellij.intellijCommon)
	testImplementation(testFixtures(projects.anvilIntegration.integrationIntellij.intellijCommon))
}
