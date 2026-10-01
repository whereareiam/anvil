plugins {
	base
}

tasks.named("build") {
	dependsOn(subprojects.map { "${it.path}:build" })
}
