package me.whereareiam.anvil.environment.provisioning.java.installation;

import me.whereareiam.anvil.api.exception.ProvisioningException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * The machine a downloaded JDK must run on, in the terms Java catalogs file their packages under.
 *
 * @param os operating system: {@code linux}, {@code mac} or {@code windows}
 * @param architecture processor architecture: {@code x64} or {@code aarch64}
 * @param libc C library a Linux JDK links against, {@code glibc} or {@code musl}; {@code null} elsewhere,
 *             where catalogs file packages under no C library
 * @param archiveType archive the platform's packages ship as: {@code zip} on Windows, {@code tar.gz} elsewhere
 */
public record JavaPlatform(
		@NotNull String os,
		@NotNull String architecture,
		@Nullable String libc,
		@NotNull String archiveType
) {
	/** Where musl's dynamic loader lives on a Linux that uses it, such as Alpine. */
	private static final Path MUSL_LOADERS = Path.of("/lib");

	/**
	 * Describes the machine this JVM runs on.
	 *
	 * @return the current platform
	 * @throws ProvisioningException when Anvil cannot download a JDK for this operating system or architecture
	 */
	public static @NotNull JavaPlatform current() {
		return of(System.getProperty("os.name", ""), System.getProperty("os.arch", ""), usesMusl());
	}

	/**
	 * Describes a machine from its Java system properties.
	 *
	 * @param osName the {@code os.name} property
	 * @param osArch the {@code os.arch} property
	 * @param musl whether a Linux machine links against musl instead of glibc
	 * @return the platform
	 * @throws ProvisioningException when Anvil cannot download a JDK for this operating system or architecture
	 */
	public static @NotNull JavaPlatform of(@NotNull String osName, @NotNull String osArch, boolean musl) {
		String os = operatingSystem(osName);
		String architecture = architecture(osArch);
		return switch (os) {
			case "linux" -> new JavaPlatform(os, architecture, musl ? "musl" : "glibc", "tar.gz");
			case "windows" -> new JavaPlatform(os, architecture, null, "zip");
			default -> new JavaPlatform(os, architecture, null, "tar.gz");
		};
	}

	/**
	 * Names the platform in a path, so installations for different platforms never share a directory. Glibc is
	 * left out, which keeps the directories Linux installations had before the C library was told apart.
	 *
	 * @return {@code <os>-<architecture>}, followed by {@code -musl} on musl
	 */
	public @NotNull String key() {
		return os + "-" + architecture + ("musl".equals(libc) ? "-musl" : "");
	}

	private static String operatingSystem(String osName) {
		String value = osName.toLowerCase(Locale.ROOT);
		if (value.startsWith("windows")) return "windows";
		if (value.contains("mac") || value.contains("darwin")) return "mac";
		if (value.contains("linux")) return "linux";

		throw new ProvisioningException("Automatic Java provisioning does not support this operating system: " + osName);
	}

	private static String architecture(String osArch) {
		return switch (osArch.toLowerCase(Locale.ROOT)) {
			case "amd64", "x86_64" -> "x64";
			case "aarch64", "arm64" -> "aarch64";
			default -> throw new ProvisioningException("Unsupported Java architecture: " + osArch);
		};
	}

	private static boolean usesMusl() {
		if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux")) return false;
		try (var loaders = Files.list(MUSL_LOADERS)) {
			return loaders.anyMatch(path -> path.getFileName().toString().startsWith("ld-musl-"));
		} catch (IOException unreadable) {
			return false;
		}
	}
}
