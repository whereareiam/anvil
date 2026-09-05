import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

val javaVersion = 21

plugins.withId("java") {
	extensions.configure<JavaPluginExtension> {
		toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
	}

	tasks.withType<JavaCompile>().configureEach {
		options.release.set(javaVersion)
		options.encoding = "UTF-8"
		options.compilerArgs.add("-parameters")
	}
}

plugins.withId("org.jetbrains.kotlin.jvm") {
	extensions.configure<KotlinJvmProjectExtension> {
		compilerOptions.jvmTarget.set(JvmTarget.fromTarget(javaVersion.toString()))
	}
}
