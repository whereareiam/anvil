plugins {
    id("vanilla-harness")
}

description = "Runs the vanilla client headless and writes reference data from it: the physics traces and the block data"

vanillaData.generate(
    liveMatrix = layout.projectDirectory.file("../../../live-matrix.toml"),
    pins = layout.projectDirectory.file("vanilla-clients.toml"),
    courses = layout.projectDirectory.dir("../courses"),
    traces = layout.projectDirectory.dir("../traces"),
    blocks = layout.projectDirectory.dir("../../../anvil-edition/edition-java/java-data/data-block/vanilla"),
)

dependencies {
    implementation(libs.jackson.toml)

    compileOnly(libs.lombok)

    annotationProcessor(libs.lombok)
}
