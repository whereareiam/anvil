package me.whereareiam.anvil.provisioning.java;

import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.api.model.java.JavaRequirement;
import me.whereareiam.anvil.api.model.java.JavaArchive;
import me.whereareiam.anvil.api.model.java.JavaSource;
import me.whereareiam.anvil.api.model.java.local.LocalJavaHome;
import me.whereareiam.anvil.api.model.java.local.LocalJavaExecutable;
import me.whereareiam.anvil.provisioning.api.artifact.ArtifactLease;
import me.whereareiam.anvil.provisioning.api.artifact.ArtifactStore;
import me.whereareiam.anvil.provisioning.api.JavaProvisioner;
import me.whereareiam.anvil.provisioning.api.model.JavaInstallation;
import me.whereareiam.anvil.provisioning.java.archive.JavaArchiveInstaller;
import me.whereareiam.anvil.provisioning.java.catalog.FoojayJavaCatalog;
import me.whereareiam.anvil.provisioning.java.distribution.GraalVmDistribution;
import me.whereareiam.anvil.provisioning.java.distribution.JavaDistributionDescriptor;
import me.whereareiam.anvil.provisioning.java.distribution.TemurinDistribution;
import me.whereareiam.anvil.provisioning.java.installation.JavaExecutables;
import me.whereareiam.anvil.provisioning.java.installation.JavaInstallationInspector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves one process Java requirement to a verified executable.
 * Local homes, user archives, current installations, and catalog packages are separate sources;
 * this class coordinates their order and owns the resulting cache identity.
 */
public final class JavaRuntimeResolver implements JavaProvisioner {
	private final Path root;
	private final ArtifactStore artifacts;
	private final boolean download;
	private final boolean refresh;
    private final FoojayJavaCatalog catalog;
	private final JavaInstallationInspector inspector;

	public JavaRuntimeResolver(@NotNull Path root, @NotNull ArtifactStore artifacts, boolean download, boolean refresh) {
		this.root = root;
		this.artifacts = artifacts;
		this.download = download;
		this.refresh = refresh;
        Map<String, JavaDistributionDescriptor> distributions = Map.of(
                "temurin", new TemurinDistribution(),
                "graalvm-community", new GraalVmDistribution()
        );

        this.catalog = new FoojayJavaCatalog(artifacts, distributions);
		this.inspector = new JavaInstallationInspector(distributions);
	}

	@Override
	public @NotNull JavaInstallation inspect(@NotNull String properties, @NotNull Path executable) {
		return inspector.inspect(properties, executable);
	}

	@Override
	public void validate(@NotNull JavaInstallation installation, @NotNull JavaRequirement requirement, int minimumVersion) {
		inspector.validate(installation, requirement, minimumVersion);
	}

	@Override
	public @NotNull Path resolve(@NotNull JavaRequirement selection, int minimumVersion) {
		return resolve(selection, minimumVersion, null);
	}

	@Override
	public @NotNull Path resolve(@NotNull JavaRequirement selection, int minimumVersion, @Nullable JavaSource source) {
		if (source instanceof LocalJavaHome home)
			return inspector.require(JavaExecutables.atHome(home.getHome()), selection, minimumVersion).getExecutable();

		if (source instanceof LocalJavaExecutable executable)
			return inspector.require(executable.getExecutable(), selection, minimumVersion).getExecutable();

		if (source instanceof JavaArchive archive) return archive(archive, selection, minimumVersion);

		Path current = JavaExecutables.current();
		if (inspector.matches(current, selection, minimumVersion)) return current;

		int version = selection.getFeatureVersion() == null ? minimumVersion : selection.getFeatureVersion();
		String environment = System.getenv("JAVA_" + version + "_HOME");
		if (environment != null) {
			Path executable = JavaExecutables.atHome(Path.of(environment));
			if (inspector.matches(executable, selection, minimumVersion)) return executable;
		}

		String distribution = selection.getDistribution() == null ? "temurin" : selection.getDistribution();
		String os = JavaExecutables.temurinOperatingSystem(System.getProperty("os.name"));
		String architecture = architecture();
		String release = selection.getRelease() == null ? "selected" : selection.getRelease();
		if (!distribution.matches("[a-z0-9-]+") || !release.matches("[a-zA-Z0-9.+_-]+"))
			throw new ProvisioningException("Invalid Java distribution or release selector");

		Path installation = root.resolve("java").resolve(distribution).resolve(version + "-" + os + "-" + architecture).resolve(release);
		try (ArtifactLease ignored = artifacts.lock(installation)) {
			Path marker = installation.resolve("executable");
			if (!refresh && Files.isRegularFile(marker)) {
				Path executable = installation.resolve(Files.readString(marker));
				if (Files.isExecutable(executable)) return inspector.require(executable, selection, minimumVersion).getExecutable();
			}

			if (!download) throw new ProvisioningException("No matching Java installation is available: " + selection);

			FoojayJavaCatalog.Package selected = catalog.select(distribution, version, selection.getRelease(), os, architecture);
			Path archive = artifacts.obtain(selected.uri(), root.resolve("java-archives").resolve(selected.sha256() + selected.extension()), selected.sha256());
			Path contents = installation.resolve(selected.sha256());
			if (!Files.isDirectory(contents)) new JavaArchiveInstaller().install(archive, contents);

			Path executable = findJava(contents);
			inspector.require(executable, selection, minimumVersion);
			Files.createDirectories(installation);
			Files.writeString(marker, installation.relativize(executable).toString());
			Files.writeString(installation.resolve("release.properties"),
					"source=" + selected.uri() + "\nsha256=" + selected.sha256() + "\n" + inspector.describe(executable));

			return executable;
		} catch (IOException failure) {
			throw new ProvisioningException("Could not prepare Java " + selection, failure);
		}
	}

	private Path archive(JavaArchive source, JavaRequirement selection, int minimumVersion) {
		Path destination = root.resolve("java-sources").resolve(source.getSha256()).resolve("jdk");
		try (ArtifactLease ignored = artifacts.lock(destination)) {
			Path executable = Files.isDirectory(destination) ? findJava(destination) : null;
			if (executable != null && Files.isExecutable(executable))
				return inspector.require(executable, selection, minimumVersion).getExecutable();

			if (!download) {
				throw new ProvisioningException("Java archive is not cached and downloads are disabled: " + source.getUri());
			}

			Path archive = artifacts.obtain(source.getUri(), archivePath(source), source.getSha256());
			new JavaArchiveInstaller().install(archive, destination);

			return inspector.require(findJava(destination), selection, minimumVersion).getExecutable();
		} catch (IOException failure) {
			throw new ProvisioningException("Could not prepare Java archive " + source.getUri(), failure);
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
