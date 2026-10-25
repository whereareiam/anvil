package me.whereareiam.anvil.workbench.remote.provisioning;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.process.ProcessFiles;
import me.whereareiam.anvil.workbench.protocol.model.file.CheckFile;
import me.whereareiam.anvil.workbench.protocol.model.file.DeleteFile;
import me.whereareiam.anvil.workbench.protocol.model.file.ListFiles;
import me.whereareiam.anvil.workbench.protocol.model.file.ReadFile;
import me.whereareiam.anvil.workbench.protocol.model.file.WriteFile;
import me.whereareiam.anvil.workbench.remote.WorkbenchConnection;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * The files of a process workspace on a workbench on another machine; each operation is one request.
 */
@RequiredArgsConstructor
final class RemoteProcessFiles implements ProcessFiles {
	private final @NotNull WorkbenchConnection connection;
	private final @NotNull Path workspace;

	@Override
	public @NotNull String location() {
		return "workbench '" + connection.pairing().getName() + "': " + workspace;
	}

	@Override
	public boolean exists(@NotNull String path) throws IOException {
		return connection.request(new CheckFile(workspace, path)).isExists();
	}

	@Override
	public @NotNull String read(@NotNull String path) throws IOException {
		return connection.request(new ReadFile(workspace, path)).getContent();
	}

	@Override
	public void write(@NotNull String path, @NotNull String content) throws IOException {
		connection.request(new WriteFile(workspace, path, content));
	}

	@Override
	public @NotNull List<String> list(@NotNull String directory) throws IOException {
		return connection.request(new ListFiles(workspace, directory)).getNames();
	}

	@Override
	public boolean delete(@NotNull String path) throws IOException {
		return connection.request(new DeleteFile(workspace, path)).isDeleted();
	}
}
