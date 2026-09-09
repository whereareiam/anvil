package me.whereareiam.anvil.capability.process;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.process.ProcessCapability;
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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shares logical-process capabilities across generation-specific views and finalizes them before processes.
 */
@RequiredArgsConstructor
final class CapabilityProcessGroup implements ProcessGroup {
	private final @NotNull ProcessGroup processes;
	private final @NotNull Map<String, ProcessCapabilities> capabilities;

	private final Map<String, ProcessView> current = new LinkedHashMap<>();
	private boolean closed;

	@Override
	public @NotNull Collection<RunningProcess> all() {
		return processes.all().stream().map(this::view).toList();
	}

	@Override
	public @NotNull RunningProcess get(@NotNull String name) {
		return view(processes.get(name));
	}

	@Override
	public @NotNull Collection<RunningServer> servers() {
		return processes.servers().stream().map(process -> (RunningServer) view(process)).toList();
	}

	@Override
	public @NotNull RunningServer server(@NotNull String name) {
		return (RunningServer) view(processes.server(name));
	}

	@Override
	public @NotNull Collection<RunningProxy> proxies() {
		return processes.proxies().stream().map(process -> (RunningProxy) view(process)).toList();
	}

	@Override
	public @NotNull RunningProxy proxy(@NotNull String name) {
		return (RunningProxy) view(processes.proxy(name));
	}

	@Override
	public @NotNull RunningProcess restart(@NotNull String name) {
		synchronized (this) {
			if (closed) throw new IllegalStateException("Cannot restart a closed scenario");
		}

		return view(processes.restart(name));
	}

	@Override
	public void finish(boolean successful) {
		synchronized (this) {
			if (closed) return;
			closed = true;
		}

		Throwable failure = ProcessCapabilityRuntime.close(capabilities.values(), null);
		try {
			processes.finish(successful && failure == null);
		} catch (RuntimeException | Error cleanup) {
			if (failure == null) failure = cleanup;
			else if (failure != cleanup) failure.addSuppressed(cleanup);
		}

		if (failure instanceof RuntimeException runtime) throw runtime;
		if (failure instanceof Error error) throw error;
	}

	private synchronized @NotNull RunningProcess view(@NotNull RunningProcess process) {
		ProcessCapabilities owner = capabilities.get(process.name());
		if (owner == null) return process;

		ProcessView existing = current.get(process.name());
		if (existing != null && existing.process == process) return existing;

		ProcessView replacement = process instanceof RunningServer
				? new ServerView(process, owner)
				: new ProxyView(process, owner);
		current.put(process.name(), replacement);

		return replacement;
	}

	@RequiredArgsConstructor
	private abstract static class ProcessView implements RunningProcess {
		private final @NotNull RunningProcess process;
		private final @NotNull ProcessCapabilities capabilities;

		@Override
		public @NotNull String name() {
			return process.name();
		}

		@Override
		public @NotNull InetSocketAddress address() {
			return process.address();
		}

		@Override
		public @NotNull Path workDirectory() {
			return process.workDirectory();
		}

		@Override
		public @NotNull ProcessState state() {
			return process.state();
		}

		@Override
		public @NotNull ProcessConsole console() {
			return process.console();
		}

		@Override
		public @NotNull <C extends ProcessCapability> C capability(@NotNull Class<C> type) {
			return capabilities.capability(type);
		}

		@Override
		public boolean hasCapability(@NotNull Class<? extends ProcessCapability> type) {
			return capabilities.hasCapability(type);
		}
	}

	private static final class ServerView extends ProcessView implements RunningServer {
		private ServerView(@NotNull RunningProcess process, @NotNull ProcessCapabilities capabilities) {
			super(process, capabilities);
		}
	}

	private static final class ProxyView extends ProcessView implements RunningProxy {
		private ProxyView(@NotNull RunningProcess process, @NotNull ProcessCapabilities capabilities) {
			super(process, capabilities);
		}
	}
}
