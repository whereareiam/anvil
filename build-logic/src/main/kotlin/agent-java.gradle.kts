plugins {
    id("unit")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.named<JavaCompile>("compileJava") {
    options.release.set(17)
}
