package me.whereareiam.anvil.workbench.remote.pairing;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.workbench.api.exception.WorkbenchException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Optional;

/**
 * Decides which workbench an engine's scenarios run on: the one the engine options name, else the one the
 * {@value #ENVIRONMENT} environment variable names, else the pairings' default, else the engine's own machine. The
 * name {@value #LOCAL} always means the engine's own machine.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WorkbenchChoice {
	/**
	 * Environment variable naming the workbench.
	 */
	public static final String ENVIRONMENT = "ANVIL_WORKBENCH";
	/**
	 * Name of the engine's own machine.
	 */
	public static final String LOCAL = "local";

	/**
	 * Returns the pairing of the chosen workbench.
	 *
	 * @param option workbench the engine options name, or null
	 * @param environment value of the environment variable, or null
	 * @param pairings the engine's pairings
	 * @return the chosen workbench's pairing, or empty for the engine's own machine
	 * @throws WorkbenchException when the chosen name is not paired
	 */
	public static @NotNull Optional<Pairing> chosen(@Nullable String option, @Nullable String environment, @NotNull Pairings pairings) {
		try {
			String name = named(option) ? option : named(environment) ? environment : pairings.defaultName().orElse(LOCAL);
			if (name.equals(LOCAL)) return Optional.empty();

			return Optional.of(pairings.find(name).orElseThrow(() -> new WorkbenchException("No workbench named '" + name
					+ "' is paired in " + pairings.directory() + "; pair it with 'java -jar anvil-workbench.jar add " + name
					+ " HOST:PORT CODE', or run on this machine with anvil.workbench=local")));
		} catch (IOException failure) {
			throw new WorkbenchException("Could not read the workbench pairings in " + pairings.directory(), failure);
		}
	}

	private static boolean named(@Nullable String name) {
		return name != null && !name.isBlank();
	}
}
