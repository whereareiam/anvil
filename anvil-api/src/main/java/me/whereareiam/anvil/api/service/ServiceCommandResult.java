package me.whereareiam.anvil.api.service;

import org.jetbrains.annotations.NotNull;

/**
 * What a command run inside a service's container returned.
 *
 * @param exitCode the command's exit code
 * @param output   what it printed, standard output and standard error together
 */
public record ServiceCommandResult(int exitCode, @NotNull String output) {
}
