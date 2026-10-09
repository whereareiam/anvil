package me.whereareiam.anvil.environment.provisioning.java;

import me.whereareiam.anvil.api.exception.JavaVersionMismatchException;
import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.api.model.java.JavaArchive;
import me.whereareiam.anvil.api.model.java.JavaRequirement;
import me.whereareiam.anvil.api.model.java.JavaSource;
import me.whereareiam.anvil.api.model.java.local.LocalJavaExecutable;
import me.whereareiam.anvil.api.model.java.local.LocalJavaHome;
import me.whereareiam.anvil.environment.provisioning.java.api.installatiion.JavaInstallationAccess;
import me.whereareiam.anvil.environment.provisioning.java.api.installatiion.JavaInstallationStorage;
import me.whereareiam.anvil.environment.provisioning.java.api.JavaPackageSource;
import me.whereareiam.anvil.environment.provisioning.java.api.JavaProvisioner;
import me.whereareiam.anvil.environment.provisioning.java.api.model.JavaInstallation;
import me.whereareiam.anvil.environment.provisioning.java.archive.JavaArchiveInstaller;
import me.whereareiam.anvil.environment.provisioning.java.catalog.FoojayJavaCatalog;
import me.whereareiam.anvil.environment.provisioning.java.distribution.GraalVmDistribution;
import me.whereareiam.anvil.environment.provisioning.java.distribution.JavaDistributionDescriptor;
import me.whereareiam.anvil.environment.provisioning.java.distribution.TemurinDistribution;
import me.whereareiam.anvil.environment.provisioning.java.installation.JavaExecutables;
import me.whereareiam.anvil.environment.provisioning.java.installation.JavaInstallationInspector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves one process Java requirement to a verified executable of exactly its feature version.
 * Local homes, user archives, current installations, and catalog packages are separate sources;
 * this class coordinates their order and owns the resulting cache identity.
 */
public final class JavaRuntimeResolver implements JavaProvisioner {
	private final @NotNull Path root;
	private final @NotNull JavaPackageSource packages;
	private final @NotNull JavaInstallationStorage storage;
	private final boolean download;
	private final boolean refresh;
	private final @NotNull FoojayJavaCatalog catalog;
	private final @NotNull JavaInstallationInspector inspector;

	/**
	 * Prepares Java installations using borrowed artifact acquisition and cache services.
	 *
	 * @param root shared cache directory containing archives and installations
	 * @param packages verified Java archives and catalog documents
	 * @param storage exclusive access to prepared Java installations
	 * @param download whether missing Java installations may be downloaded
	 * @param refresh whether catalog selections should be refreshed
	 */
	public JavaRuntimeResolver(
			@NotNull Path root,
			@NotNull JavaPackageSource packages,
			@NotNull JavaInstallationStorage storage,
			boolean download,
			boolean refresh
	) {
		this.root = root;
		this.packages = packages;
		this.storage = storage;
		this.download = download;
		this.refresh = refresh;
		Map<String, JavaDistributionDescriptor> distributions = Map.of(
				"temurin", new TemurinDistribution(),
				"graalvm-community", new GraalVmDistribution()
		);

		this.catalog = new FoojayJavaCatalog(packages, distributions);
		this.inspector = new JavaInstallationInspector(distributions);
	}

	@Override
	public @NotNull JavaInstallation inspect(@NotNull String properties, @NotNull Path executable) {
		return inspector.inspect(properties, executable);
	}

	@Override
	public void validate(@NotNull JavaInstallation installation, @NotNull JavaRequirement requirement) {
		inspector.validate(installation, requirement);
	}

	@Override
	public @NotNull Path resolve(@NotNull JavaRequirement selection, @Nullable JavaSource source) {
		int version = inspector.featureVersion(selection);
		if (source instanceof LocalJavaHome home)
			return requireSource(JavaExecutables.atHome(home.getHome()), selection, version);

		if (source instanceof LocalJavaExecutable executable)
			return requireSource(executable.getExecutable(), selection, version);

		if (source instanceof JavaArchive archive) return archive(archive, selection, version);

		Path current = JavaExecutables.current();
		if (inspector.matches(current, selection)) return current;

		String variable = "JAVA_" + version + "_HOME";
		String environment = System.getenv(variable);
		if (environment != null) {
			Path executable = JavaExecutables.atHome(Path.of(environment));
			if (inspector.matches(executable, selection)) return executable;
		}

		String distribution = selection.getDistribution() == null ? "temurin" : selection.getDistribution();
		String os = JavaExecutables.temurinOperatingSystem(System.getProperty("os.name"));
		String architecture = architecture();
		String release = selection.getRelease() == null ? "selected" : selection.getRelease();
		if (!distribution.matches("[a-z0-9-]+") || !release.matches("[a-zA-Z0-9.+_-]+"))
			throw new ProvisioningException("Invalid Java distribution or release selector");

		Path installation = root.resolve("java").resolve(distribution).resolve(version + "-" + os + "-" + architecture).resolve(release);
		try (JavaInstallationAccess ignored = storage.acquire(installation)) {
			Path marker = installation.resolve("executable");
			if (!refresh && Files.isRegularFile(marker)) {
				Path executable = installation.resolve(Files.readString(marker));
				if (Files.isExecutable(executable)) return inspector.require(executable, selection).getExecutable();
			}

			if (!download)
				throw new ProvisioningException("No Java " + version + " installation matches " + selection
						+ ": the current JVM is Java " + Runtime.version().feature() + " (" + current + "), "
						+ (environment == null ? variable + " is not set" : variable + " points to " + environment
						+ ", which does not match") + ", and nothing is cached at " + installation + ". Set " + variable
						+ " or enable downloads with anvil.java.download=true.");

			FoojayJavaCatalog.Package selected = catalog.select(distribution, version, selection.getRelease(), os, architecture);
			Path archive = packages.archive(JavaArchive.builder().uri(selected.uri()).sha256(selected.sha256()).build(),
					root.resolve("java-archives").resolve(selected.sha256() + selected.extension()));
			Path contents = installation.resolve(selected.sha256());
			if (!Files.isDirectory(contents)) new JavaArchiveInstaller().install(archive, contents);

			Path executable = findJava(contents);
			inspector.require(executable, selection);
			Files.createDirectories(installation);
			Files.writeString(marker, installation.relativize(executable).toString());
			Files.writeString(installation.resolve("release.properties"),
					"source=" + selected.uri() + "\nsha256=" + selected.sha256() + "\n" + inspector.describe(executable));

			return executable;
		} catch (IOException failure) {
			throw new ProvisioningException("Could not prepare Java " + selection, failure);
		}
	}

	/**
	 * Requires an explicitly declared local Java. Anvil uses nothing else while the source is declared, so a
	 * mismatch names the fix in the declaration rather than the variables of the automatic lookup.
	 */
	private Path requireSource(Path executable, JavaRequirement selection, int version) {
		try {
			return inspector.require(executable, selection).getExecutable();
		} catch (JavaVersionMismatchException mismatch) {
			throw new ProvisioningException(mismatch.getMessage() + "; point the Java source at Java " + version
					+ ", or remove it so Anvil uses JAVA_" + version + "_HOME, the cache or a download", mismatch);
		}
	}

	private Path archive(JavaArchive source, JavaRequirement selection, int version) {
		Path destination = root.resolve("java-sources").resolve(source.getSha256()).resolve("jdk");
		try (JavaInstallationAccess ignored = storage.acquire(destination)) {
			Path executable = Files.isDirectory(destination) ? findJava(destination) : null;
			if (executable != null && Files.isExecutable(executable))
				return requireArchive(executable, selection, source, version);

			if (!download)
				throw new ProvisioningException("Java archive is not cached and downloads are disabled: " + source.getUri());

			Path archive = packages.archive(source, archivePath(source));
			new JavaArchiveInstaller().install(archive, destination);

			return requireArchive(findJava(destination), selection, source, version);
		} catch (IOException failure) {
			throw new ProvisioningException("Could not prepare Java archive " + source.getUri(), failure);
		}
	}

	private Path requireArchive(Path executable, JavaRequirement selection, JavaArchive source, int version) {
		try {
			return inspector.require(executable, selection).getExecutable();
		} catch (JavaVersionMismatchException mismatch) {
			throw new ProvisioningException(mismatch.getMessage() + "; point the Java archive source " + source.getUri()
					+ " at a Java " + version + " package, or remove it so Anvil uses JAVA_" + version
					+ "_HOME, the cache or a download", mismatch);
		}
	}

	private Path archivePath(JavaArchive source) {
		String path = source.getUri().getPath();
		String extension = path != null && path.toLowerCase(Locale.ROOT).endsWith(".zip") ? ".zip" : ".tar.gz";
		return root.resolve("java-archives").resolve(source.getSha256() + extension);
	}

	private Path findJava(Path root) throws IOException {
		String filename = JavaExecutables.executableName(System.getProperty("os.name", ""));
		try (var files = Files.find(root, 8, (path, attributes) -> attributes.isRegularFile()
				&& path.getFileName().toString().equals(filename)
				&& path.getParent().getFileName().toString().equals("bin"))) {
			return files.findFirst()
					.orElseThrow(() -> new ProvisioningException("JDK archive contains no Java executable: " + root));
		}
	}

	private String architecture() {
		return switch (System.getProperty("os.arch", "").toLowerCase(Locale.ROOT)) {
			case "amd64", "x86_64" -> "x64";
			case "aarch64", "arm64" -> "aarch64";
			default -> throw new ProvisioningException("Unsupported Java architecture: " + System.getProperty("os.arch"));
		};
	}

}
