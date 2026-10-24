package me.whereareiam.anvil.workbench.remote.provisioning;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.process.ProcessFiles;
import me.whereareiam.anvil.workbench.remote.WorkbenchConnection;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
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
		return connection.request("files.exists", arguments(path)).asBoolean();
	}

	@Override
	public @NotNull String read(@NotNull String path) throws IOException {
		return connection.request("files.read", arguments(path)).asText();
	}

	@Override
	public void write(@NotNull String path, @NotNull String content) throws IOException {
		connection.request("files.write", arguments(path).put("content", content));
	}

	@Override
	public @NotNull List<String> list(@NotNull String directory) throws IOException {
		List<String> names = new ArrayList<>();
		connection.request("files.list", arguments(directory)).forEach(name -> names.add(name.asText()));
		return names;
	}

	@Override
	public boolean delete(@NotNull String path) throws IOException {
		return connection.request("files.delete", arguments(path)).asBoolean();
	}

	private ObjectNode arguments(String path) {
		return connection.mapper().createObjectNode().put("workspace", workspace.toString()).put("path", path);
	}
}
