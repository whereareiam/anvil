package me.whereareiam.anvil.environment.provisioning.workspace.directory;

import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;

/**
 * Safe operations confined to generated Anvil workspaces.
 */
public final class WorkspaceFiles {
	/**
	 * Resolves a user-supplied path below a workspace and rejects absolute or traversing paths.
	 *
	 * @param workspace workspace root
	 * @param relative path declared by a scenario
	 * @param label description used in validation errors
	 * @return normalized path below {@code workspace}
	 */
	public Path resolveRelative(Path workspace, Path relative, String label) {
		if (relative == null || relative.isAbsolute())
			throw new ScenarioValidationException(label + " must be relative to the process workspace: " + relative);

		for (Path part : relative)
			if (part.toString().equals(".."))
				throw new ScenarioValidationException(label + " must not traverse outside the process workspace: " + relative);

		Path normalizedWorkspace = workspace.toAbsolutePath().normalize();
		Path normalized = normalizedWorkspace.resolve(relative).normalize();
		if (!normalized.startsWith(normalizedWorkspace) || normalized.equals(normalizedWorkspace))
			throw new ScenarioValidationException(label + " must point below the process workspace: " + relative);
		Path current = normalizedWorkspace;
		for (Path part : relative) {
			current = current.resolve(part);
			if (Files.isSymbolicLink(current))
				throw new ScenarioValidationException(label + " must not pass through a symbolic link: " + relative);
		}
		return normalized;
	}

	/**
	 * Deletes a workspace-relative file or directory when it exists.
	 *
	 * @param workspace workspace root
	 * @param relative path declared by a scenario
	 */
	public void delete(Path workspace, Path relative) {
		deleteAbsolute(resolveRelative(workspace, relative, "Cleanup path"));
	}

	/**
	 * Copies a file to the declared target or copies a directory's contents into the target directory.
	 *
	 * @param source source file or directory
	 * @param workspace workspace root
	 * @param relativeTarget target path relative to the workspace
	 */
	public void copy(Path source, Path workspace, Path relativeTarget) {
		if (source == null || !Files.exists(source))
			throw new ProvisioningException("Workspace asset does not exist: " + source);

		Path normalizedSource = source.toAbsolutePath().normalize();
		Path target = resolveRelative(workspace, relativeTarget, "Asset target");
		try {
			if (Files.isSymbolicLink(normalizedSource))
				throw new ScenarioValidationException("Workspace asset source must not be a symbolic link: " + source);
			if (Files.isRegularFile(normalizedSource)) {
				Files.createDirectories(target.getParent());
				Files.copy(normalizedSource, target, StandardCopyOption.REPLACE_EXISTING);
				return;
			}

			if (!Files.isDirectory(normalizedSource))
				throw new ProvisioningException("Workspace asset is neither a file nor a directory: " + source);
			Files.createDirectories(target);
			try (var paths = Files.walk(normalizedSource)) {
				for (Path path : paths.toList()) {
					if (Files.isSymbolicLink(path))
						throw new ScenarioValidationException("Workspace asset tree contains a symbolic link: " + path);
					Path destination = target.resolve(normalizedSource.relativize(path).toString()).normalize();
					if (!destination.startsWith(target))
						throw new ScenarioValidationException("Workspace asset escapes its target directory: " + source);
					if (Files.isDirectory(path)) {
						Files.createDirectories(destination);
						continue;
					}
					Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		} catch (IOException e) {
			throw new ProvisioningException("Could not copy workspace asset " + source + " to " + relativeTarget, e);
		}
	}

	/**
	 * Copies an existing tree into a destination, replacing files with the same relative name.
	 *
	 * @param source source file or directory
	 * @param destination destination path
	 */
	public void copyAbsolute(Path source, Path destination) {
		if (!Files.exists(source))
			throw new ProvisioningException("Source does not exist: " + source);
		try {
			if (Files.isSymbolicLink(source))
				throw new ProvisioningException("Workspace cache path must not be a symbolic link: " + source);
			if (Files.isRegularFile(source)) {
				if (destination.getParent() != null)
					Files.createDirectories(destination.getParent());
				Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
				return;
			}
			Files.createDirectories(destination);
			try (var paths = Files.walk(source)) {
				for (Path path : paths.toList()) {
					if (Files.isSymbolicLink(path))
						throw new ProvisioningException("Workspace tree contains a symbolic link: " + path);
					Path target = destination.resolve(source.relativize(path).toString());
					if (Files.isDirectory(path)) {
						Files.createDirectories(target);
						continue;
					}
					Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		} catch (IOException e) {
			throw new ProvisioningException("Could not copy " + source + " to " + destination, e);
		}
	}

	/**
	 * Resolves a directory strictly below the configured root without following nested symbolic links.
	 * The configured root itself is caller-selected and may be a symbolic link.
	 *
	 * @param root configured workspace root
	 * @param directory requested process or run directory
	 * @return normalized directory confined below the configured root
	 */
	public @NotNull Path resolveDirectory(@NotNull Path root, @NotNull Path directory) {
		Path normalizedRoot = root.toAbsolutePath().normalize();
		Path normalizedDirectory = directory.toAbsolutePath().normalize();
		if (normalizedDirectory.equals(normalizedRoot) || !normalizedDirectory.startsWith(normalizedRoot))
			throw new ScenarioValidationException("Workspace directory is outside the configured root: " + directory);

		Path current = normalizedRoot;
		for (Path component : normalizedRoot.relativize(normalizedDirectory)) {
			current = current.resolve(component);
			if (Files.isSymbolicLink(current))
				throw new ScenarioValidationException("Workspace directory must not pass through a symbolic link: " + directory);
		}

		return normalizedDirectory;
	}

	/**
	 * Recreates a generated directory below the configured workspace root.
	 *
	 * @param root configured workspace root
	 * @param target generated directory
	 */
	public void recreate(Path root, Path target) {
		Path normalizedTarget = resolveDirectory(root, target);
		try {
			deleteAbsolute(normalizedTarget);
			Files.createDirectories(normalizedTarget);
		} catch (IOException e) {
			throw new ProvisioningException("Could not recreate workspace " + normalizedTarget, e);
		}
	}

	/**
	 * Deletes an absolute path tree. The caller must have validated that the path is confined.
	 *
	 * @param target path to delete
	 */
	public void deleteAbsolute(Path target) {
		try {
			if (!Files.exists(target))
				return;
			try (var paths = Files.walk(target)) {
				for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
					Files.deleteIfExists(path);
			}
		} catch (IOException e) {
			throw new ProvisioningException("Could not delete workspace path " + target, e);
		}
	}

	/**
	 * Copies a file or directory overlay into a workspace.
	 *
	 * @param source overlay source
	 * @param destination workspace root
	 */
	public void overlay(Path source, Path destination) {
		if (!Files.exists(source))
			throw new ProvisioningException("Overlay does not exist: " + source);
		copyAbsolute(source, Files.isDirectory(source) ? destination : destination.resolve(source.getFileName()));
	}

}
