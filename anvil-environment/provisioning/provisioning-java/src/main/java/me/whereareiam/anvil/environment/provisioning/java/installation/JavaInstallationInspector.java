package me.whereareiam.anvil.environment.provisioning.java.installation;

import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.api.model.java.JavaRequirement;
import me.whereareiam.anvil.environment.provisioning.java.api.model.JavaInstallation;
import me.whereareiam.anvil.environment.provisioning.java.distribution.JavaDistributionDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Inspects Java executables once and validates their typed identity. */
public final class JavaInstallationInspector {
	private final Map<Path, JavaInstallation> installations = new ConcurrentHashMap<>();
	private final Map<String, JavaDistributionDescriptor> distributions;
	private final Map<Path, String> properties = new ConcurrentHashMap<>();

	public JavaInstallationInspector(Map<String, JavaDistributionDescriptor> distributions) {
		this.distributions = Map.copyOf(distributions);
	}

	public JavaInstallation inspect(@NotNull String properties, @NotNull Path executable) {
		Path path = executable.toAbsolutePath().normalize();
		this.properties.put(path, properties);
		String version = property(properties, "java.version");

		return JavaInstallation.builder().executable(path)
				.featureVersion(Runtime.Version.parse(version).feature()).version(version)
				.runtimeVersion(property(properties, "java.runtime.version"))
				.vendor(property(properties, "java.vendor"))
				.virtualMachine(property(properties, "java.vm.name"))
				.build();
	}

	public String describe(@NotNull Path executable) {
		Path path = executable.toAbsolutePath().normalize();
		if (!properties.containsKey(path)) inspect(path);

		return properties.get(path);
	}

	public JavaInstallation require(@NotNull Path executable, @NotNull JavaRequirement requirement, int minimumVersion) {
		JavaInstallation installation = installations.computeIfAbsent(executable.toAbsolutePath().normalize(), this::inspect);
		validate(installation, requirement, minimumVersion);
		return installation;
	}

	public boolean matches(Path executable, JavaRequirement requirement, int minimumVersion) {
		if (!Files.isExecutable(executable)) return false;

		try {
			require(executable, requirement, minimumVersion);
			return true;
		} catch (ProvisioningException mismatch) {
			return false;
		}
	}

	private JavaInstallation inspect(Path executable) {
		try {
			Path output = Files.createTempFile("anvil-java-", ".properties");

			try {
				Process process = new ProcessBuilder(executable.toString(), "-XshowSettings:properties", "-version")
						.redirectErrorStream(true).redirectOutput(output.toFile()).start();
				if (!process.waitFor(Duration.ofSeconds(20).toMillis(), TimeUnit.MILLISECONDS)) {
					process.destroyForcibly();
					throw new ProvisioningException("Timed out inspecting Java: " + executable);
				}

				if (process.exitValue() != 0) throw new ProvisioningException("Java inspection failed: " + executable);
				return inspect(Files.readString(output), executable);
			} finally {
				Files.deleteIfExists(output);
			}
		} catch (IOException failure) {
			throw new ProvisioningException("Could not inspect Java: " + executable, failure);
		} catch (InterruptedException failure) {
			Thread.currentThread().interrupt();
			throw new ProvisioningException("Interrupted inspecting Java: " + executable, failure);
		}
	}

	public void validate(JavaInstallation installation, JavaRequirement requirement, int minimumVersion) {
		if (installation.getFeatureVersion() < minimumVersion
				|| requirement.getFeatureVersion() != null && installation.getFeatureVersion() != requirement.getFeatureVersion())
			throw new ProvisioningException("Java " + installation.getVersion() + " does not meet " + requirement);
		if (requirement.getRelease() != null
				&& !requirement.getRelease().equals(installation.getVersion())
				&& !requirement.getRelease().equals(installation.getRuntimeVersion()))
			throw new ProvisioningException("Java release mismatch: requested " + requirement.getRelease());

		String distribution = requirement.getDistribution();
		if (distribution == null) return;

		JavaDistributionDescriptor provider = distributions.get(distribution);
		if (provider == null) throw new ProvisioningException("Unsupported Java distribution: " + distribution);
		boolean matches = provider.matches(installation);

		if (!matches) throw new ProvisioningException("Java distribution mismatch: requested " + distribution);
	}

	private String property(String properties, String name) {
		var matcher = Pattern.compile("(?m)^\\s*" + Pattern.quote(name) + " = (.+)$").matcher(properties);
		if (!matcher.find()) throw new ProvisioningException("Java inspection did not report " + name);

		return matcher.group(1).trim();
	}
}
