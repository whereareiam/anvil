import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
	`java-library`
	id("org.jetbrains.intellij.platform")
	alias(libs.plugins.kotlin.jvm)
	alias(libs.plugins.kotlin.lombok)
	alias(libs.plugins.toolkit.architecture)
	id("unit")
}

description = "IntelliJ presentation and platform adapters for Anvil"

// The baseline IDE supplies Kotlin 2.1; do not emit calls requiring a newer runtime.
kotlin {
	compilerOptions {
		apiVersion.set(KotlinVersion.KOTLIN_2_1)
		languageVersion.set(KotlinVersion.KOTLIN_2_2)
	}
}

dependencies {
	implementation(projects.anvilApi)
	implementation(projects.anvilIntegration.integrationIntellij.intellijApi)

	compileOnly(projects.anvilTooling.toolingApi)

	testImplementation(projects.anvilIntegration.integrationIntellij.intellijEngine)
	testImplementation(testFixtures(projects.anvilIntegration.integrationIntellij.intellijEngine))
	testImplementation(projects.anvilTooling.toolingApi)
	testImplementation(libs.junit4)

	testRuntimeOnly(libs.junit.vintage)

	intellijPlatform {
		create("IC", libs.versions.intellij.idea)
		bundledPlugin("com.intellij.java")
		bundledPlugin("org.jetbrains.plugins.gradle")
		testFramework(TestFrameworkType.Platform)
	}
}

// Native tests supply presentation resources without depending on the packaging project.
tasks.processTestResources {
	from(rootProject.file("assets/branding/anvil-transparent.svg")) {
		into("icons")
		rename { "anvil.svg" }
	}
}

val verificationIdePath = providers.gradleProperty("anvil.intellijVerificationPath")

intellijPlatformTesting.testIde.register("testCurrentIde") {
	type = IntelliJPlatformType.IntellijIdeaUltimate
	version = libs.versions.intellij.verify
	if (verificationIdePath.isPresent) localPath.set(file(verificationIdePath.get()))
	testFramework(TestFrameworkType.Platform, task.map { it.productInfo.buildNumber })
	plugins {
		bundledPlugins("com.intellij.java", "org.jetbrains.plugins.gradle")
		disablePlugin("com.intellij.modules.ultimate")
	}
	task {
		useJUnitPlatform()
		classpath += sourceSets.test.get().output
	}
}

// Screenshot capture is opt-in: add -Panvil.uiCaptures to write test renders under build/reports.
if (providers.gradleProperty("anvil.uiCaptures").isPresent) {
	val captures = layout.buildDirectory.dir("reports/ui-captures").get().asFile.path
	tasks.test {
		systemProperty("anvil.uiCaptures", captures)
	}
}

tasks.named("instrumentTestCode") {
	mustRunAfter(tasks.named("instrumentCode"))
}
