package me.whereareiam.anvil.gradle.internal

internal object AnvilCoordinates {
    private const val GROUP = "me.whereareiam.anvil"

    fun module(artifact: String, version: String): String =
        "$GROUP:$artifact:$version"
}
