plugins {
	id("io.freefair.lombok")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
	add("compileOnly", libs.findLibrary("annotations").get())
}
