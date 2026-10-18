package me.whereareiam.anvil.platform.api;

import me.whereareiam.anvil.api.model.java.JavaSource;
import org.jetbrains.annotations.NotNull;

import java.util.OptionalInt;

/**
 * Tells planning which Java version a declared Java source is. When a process declares a local Java, a home or an
 * executable, and requests no Java version, planning runs that Java's version if the process's platform accepts it,
 * and refuses the process if it does not, instead of planning a version the declared Java cannot run. Java
 * provisioning reads the version; planning stays independent of it.
 *
 * <pre>{@code
 * PlatformJavaSource sources = source -> provisioner.inspect(source)
 *         .map(installation -> OptionalInt.of(installation.getFeatureVersion()))
 *         .orElse(OptionalInt.empty());
 * }</pre>
 */
public interface PlatformJavaSource {
	/**
	 * Returns the Java feature version of a declared source.
	 *
	 * @param source the declared source
	 * @return the source's feature version, or empty when it is known only once execution prepares it, as for an
	 *         archive that is not downloaded yet
	 * @throws me.whereareiam.anvil.api.exception.ProvisioningException when a local source names no runnable Java
	 */
	@NotNull OptionalInt featureVersion(@NotNull JavaSource source);
}
