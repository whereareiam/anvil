package me.whereareiam.anvil.gradle.type

internal enum class AnvilDependencyBucket(
    val configurationName: String,
    val description: String,
) {
    FRAMEWORK("anvilFramework", "Anvil compile-time framework modules"),
    LAUNCHER("anvilLauncher", "Anvil executable launcher distribution"),
    CAPABILITIES("anvilCapabilities", "Explicit Anvil player and agent capabilities"),
    PROTOCOLS("anvilProtocols", "Selected Anvil protocol providers"),
    PLATFORMS("anvilPlatforms", "Anvil platform providers and agents"),
}
