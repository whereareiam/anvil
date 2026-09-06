plugins {
	id("io.freefair.lombok")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

lombok {
	version.set(libs.findVersion("lombok").get().requiredVersion)
}

dependencies {
	add("compileOnly", libs.findLibrary("annotations").get())
}
