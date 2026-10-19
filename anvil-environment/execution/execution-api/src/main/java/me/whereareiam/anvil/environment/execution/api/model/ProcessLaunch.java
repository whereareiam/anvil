package me.whereareiam.anvil.environment.execution.api.model;

/**
 * What a process target starts for one generation: a Java application from a host JAR, or a container image.
 */
public sealed interface ProcessLaunch permits JavaCommand, ImageLaunch {
}
