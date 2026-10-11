package me.whereareiam.anvil.engine.process;

import me.whereareiam.anvil.api.process.ProcessConsole;
import me.whereareiam.anvil.api.process.ProcessGroup;
import me.whereareiam.anvil.api.process.RunningProcess;
import me.whereareiam.anvil.api.process.type.RunningProxy;
import me.whereareiam.anvil.api.process.type.RunningServer;
import me.whereareiam.anvil.api.type.ProcessState;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * A process group of named processes that share one state and record what is done to the group.
 */
final class RecordingGroup implements ProcessGroup {
	final List<String> calls;
	private final String label;
	private final List<String> names;
	ProcessState state = ProcessState.READY;
	RuntimeException finishFailure;

	RecordingGroup(String label, List<String> calls, String... names) {
		this.label = label;
		this.calls = calls;
		this.names = List.of(names);
	}

	@Override
	public void startAll() {
		calls.add(label + ":startAll");
	}

	@Override
	public void finish(boolean successful) {
		calls.add(label + ":finish:" + successful);
		state = ProcessState.STOPPED;
		if (finishFailure != null) throw finishFailure;
	}

	@Override
	public @NotNull Collection<RunningProcess> all() {
		return names.stream().map(this::process).toList();
	}

	@Override
	public @NotNull RunningProcess get(@NotNull String name) {
		if (!names.contains(name)) throw new NoSuchElementException(label + " has no process " + name);
		return process(name);
	}

	@Override
	public @NotNull Collection<RunningServer> servers() {
		return List.of();
	}

	@Override
	public @NotNull RunningServer server(@NotNull String name) {
		throw new UnsupportedOperationException();
	}

	@Override
	public @NotNull Collection<RunningProxy> proxies() {
		return List.of();
	}

	@Override
	public @NotNull RunningProxy proxy(@NotNull String name) {
		throw new UnsupportedOperationException();
	}

	@Override
	public @NotNull RunningProcess start(@NotNull String name) {
		calls.add(label + ":start:" + name);
		return get(name);
	}

	@Override
	public void stop(@NotNull String name) {
		calls.add(label + ":stop:" + name);
	}

	@Override
	public @NotNull RunningProcess restart(@NotNull String name) {
		calls.add(label + ":restart:" + name);
		return get(name);
	}

	private RunningProcess process(String name) {
		return new RunningProcess() {
			@Override
			public @NotNull UUID executionId() { return UUID.nameUUIDFromBytes((label + name).getBytes()); }

			@Override
			public @NotNull String name() { return name; }

			@Override
			public @NotNull InetSocketAddress address() { return new InetSocketAddress("127.0.0.1", 25565); }

			@Override
			public @NotNull Path workDirectory() { return Path.of(label, name); }

			@Override
			public @NotNull ProcessState state() { return state; }

			@Override
			public @NotNull ProcessConsole console() { throw new UnsupportedOperationException(); }
		};
	}
}
