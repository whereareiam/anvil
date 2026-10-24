package me.whereareiam.anvil.environment.provisioning.workspace.directory;

import me.whereareiam.anvil.api.process.ProcessFiles;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * The files of a process workspace on this machine, confined to the workspace as scenario-declared paths are.
 */
public final class WorkspaceProcessFiles implements ProcessFiles {
	private static final String LABEL = "Process file";

	private final @NotNull Path workspace;
	private final @NotNull WorkspaceFiles files;

	/**
	 * Opens the files of one workspace; the directory itself need not exist yet.
	 *
	 * @param workspace workspace root
	 * @param files confined workspace operations
	 */
	public WorkspaceProcessFiles(@NotNull Path workspace, @NotNull WorkspaceFiles files) {
		this.workspace = workspace.toAbsolutePath().normalize();
		this.files = files;
	}

	@Override
	public @NotNull String location() {
		return workspace.toString();
	}

	@Override
	public boolean exists(@NotNull String path) {
		return Files.exists(resolve(path));
	}

	@Override
	public @NotNull String read(@NotNull String path) throws IOException {
		return Files.readString(resolve(path), StandardCharsets.UTF_8);
	}

	@Override
	public void write(@NotNull String path, @NotNull String content) throws IOException {
		Path file = resolve(path);
		Files.createDirectories(file.getParent());
		Files.writeString(file, content, StandardCharsets.UTF_8);
	}

	@Override
	public @NotNull List<String> list(@NotNull String directory) throws IOException {
		Path resolved = directory.isEmpty() ? workspace : resolve(directory);
		try (Stream<Path> entries = Files.list(resolved)) {
			return entries.map(entry -> entry.getFileName().toString()).sorted().toList();
		}
	}

	@Override
	public boolean delete(@NotNull String path) {
		Path target = resolve(path);
		if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return false;

		files.deleteAbsolute(target);
		return true;
	}

	private Path resolve(String path) {
		return files.resolveRelative(workspace, Path.of(path), LABEL);
	}
}
