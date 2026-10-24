package me.whereareiam.anvil.workbench.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.NetworkPolicy;
import me.whereareiam.anvil.api.model.java.JavaRequirement;
import me.whereareiam.anvil.api.model.java.JavaSource;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.model.workspace.WorkspaceCache;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import me.whereareiam.anvil.api.process.ProcessFiles;
import me.whereareiam.anvil.api.process.type.RunningContainer;
import me.whereareiam.anvil.api.type.ProcessPriority;
import me.whereareiam.anvil.environment.execution.api.ExecutionProvider;
import me.whereareiam.anvil.environment.execution.api.ExecutionSession;
import me.whereareiam.anvil.environment.execution.api.model.ProcessLaunch;
import me.whereareiam.anvil.environment.execution.api.model.process.ProcessRequest;
import me.whereareiam.anvil.environment.execution.api.process.ProcessExecution;
import me.whereareiam.anvil.environment.execution.api.process.ProcessTarget;
import me.whereareiam.anvil.environment.provisioning.java.api.model.JavaInstallation;
import me.whereareiam.anvil.environment.provisioning.workspace.api.PreparedWorkspace;
import me.whereareiam.anvil.environment.provisioning.workspace.api.model.WorkspaceRequest;
import me.whereareiam.anvil.workbench.api.Workbench;
import me.whereareiam.anvil.workbench.api.model.JavaMainLaunch;
import me.whereareiam.anvil.workbench.protocol.WireProtocol;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Carries out one engine's requests on the workbench: what a {@link Workbench} offers, addressed through handles
 * for the objects that outlive one request.
 */
@RequiredArgsConstructor
final class EngineOperations {
	private final @NotNull Workbench workbench;
	private final @NotNull ServerSettings settings;
	private final @NotNull Handles handles;
	private final @NotNull UploadStore uploads;
	private final @NotNull ObjectMapper mapper;
	private final @NotNull String owner;
	private final @NotNull Exposures exposures;

	/**
	 * Carries out one request.
	 *
	 * @param operation operation name
	 * @param arguments its arguments
	 * @return its result, {@code null} JSON when it has none
	 * @throws Exception the failure the engine receives
	 */
	@NotNull JsonNode handle(@NotNull String operation, @NotNull JsonNode arguments) throws Exception {
		return switch (operation) {
			case "hello" -> hello(arguments);
			case "facts" -> mapper.valueToTree(settings.restrict(workbench.facts()));
			case "store.find" -> nullable(uploads.find(arguments.path("sha256").asText(), arguments.path("name").asText()));
			case "launch" -> launch(arguments);
			case "launched.waitFor" -> waitFor(arguments);
			case "launched.destroy" -> destroy(arguments);
			case "artifacts.obtain" -> text(workbench.artifacts().obtain(URI.create(arguments.path("uri").asText()),
					path(arguments, "destination"), arguments.path("sha256").isNull() ? null : arguments.path("sha256").asText()));
			case "artifacts.read" -> mapper.getNodeFactory().textNode(workbench.artifacts().read(URI.create(arguments.path("uri").asText())));
			case "java.resolve" -> text(workbench.java().resolve(value(arguments, "requirement", JavaRequirement.class),
					optional(arguments, "source", JavaSource.class)));
			case "java.inspectSource" -> inspect(workbench.java().inspect(value(arguments, "source", JavaSource.class)));
			case "java.inspect" -> mapper.valueToTree(workbench.java().inspect(arguments.path("properties").asText(),
					path(arguments, "executable")));
			case "java.validate" -> validate(arguments);
			case "workspaces.layout" -> mapper.valueToTree(workbench.workspaces().layout(path(arguments, "root"),
					value(arguments, "scenario", AnvilScenario.class)));
			case "workspaces.prepare" -> prepare(arguments);
			case "workspaces.finish" -> finishWorkspace(arguments);
			case "workspaces.recreateRun" -> run(() -> workbench.workspaces().recreateRunDirectory(path(arguments, "root"), path(arguments, "directory")));
			case "workspaces.finishRun" -> run(() -> workbench.workspaces().finishRunDirectory(path(arguments, "root"),
					path(arguments, "directory"), arguments.path("successful").asBoolean(), arguments.path("keep").asBoolean()));
			case "workspaces.deleteRun" -> run(() -> workbench.workspaces().deleteRunDirectory(path(arguments, "root"), path(arguments, "directory")));
			case "files.exists" -> mapper.getNodeFactory().booleanNode(files(arguments).exists(arguments.path("path").asText()));
			case "files.read" -> mapper.getNodeFactory().textNode(files(arguments).read(arguments.path("path").asText()));
			case "files.write" -> run(() -> files(arguments).write(arguments.path("path").asText(), arguments.path("content").asText()));
			case "files.list" -> mapper.valueToTree(files(arguments).list(arguments.path("path").asText()));
			case "files.delete" -> mapper.getNodeFactory().booleanNode(files(arguments).delete(arguments.path("path").asText()));
			case "executions" -> executions();
			case "execution.open" -> open(arguments);
			case "session.prepare" -> prepareTarget(arguments);
			case "session.close" -> close(arguments, ExecutionSession.class);
			case "target.start" -> start(arguments);
			case "target.close" -> close(arguments, ProcessTarget.class);
			case "process.alive" -> mapper.getNodeFactory().booleanNode(execution(arguments).isAlive());
			case "process.await" -> mapper.getNodeFactory().booleanNode(execution(arguments)
					.await(Duration.ofMillis(arguments.path("timeoutMillis").asLong())));
			case "process.terminate" -> run(() -> execution(arguments).terminate(arguments.path("force").asBoolean()));
			case "process.freeze" -> run(() -> execution(arguments).freeze());
			case "process.thaw" -> run(() -> execution(arguments).thaw());
			case "process.execute" -> executeIn(arguments);
			case "process.forget" -> forget(arguments);
			case "expose" -> run(() -> exposures.expose(arguments.path("port").asInt()));
			default -> throw new UnsupportedOperationException("The workbench does not know operation '" + operation + "'");
		};
	}

	private JsonNode hello(JsonNode arguments) {
		int version = arguments.path("version").asInt();
		if (version != WireProtocol.VERSION)
			throw new IllegalStateException("This workbench speaks protocol " + WireProtocol.VERSION + ", but the engine speaks "
					+ version + "; run the same Anvil version on both");

		return mapper.createObjectNode()
				.put("name", settings.getName())
				.put("workDirectory", workbench.workDirectory().toString())
				.put("cacheDirectory", workbench.cacheDirectory().toString());
	}

	private JsonNode launch(JsonNode arguments) throws Exception {
		Process process = workbench.launch(value(arguments, "launch", JavaMainLaunch.class));
		String handle = handles.register(owner, process, () -> process.destroyForcibly());
		return mapper.getNodeFactory().textNode(handle);
	}

	private JsonNode waitFor(JsonNode arguments) throws InterruptedException {
		Process process = handles.get(owner, arguments.path("handle").asText(), Process.class);
		long timeout = arguments.path("timeoutMillis").asLong(-1);
		if (timeout < 0) return mapper.getNodeFactory().numberNode(process.waitFor());
		if (!process.waitFor(timeout, TimeUnit.MILLISECONDS)) return NullNode.getInstance();

		return mapper.getNodeFactory().numberNode(process.exitValue());
	}

	private JsonNode destroy(JsonNode arguments) {
		Process process = handles.get(owner, arguments.path("handle").asText(), Process.class);
		if (arguments.path("force").asBoolean()) process.destroyForcibly();
		else process.destroy();
		return NullNode.getInstance();
	}

	private JsonNode inspect(Optional<JavaInstallation> installation) {
		return installation.<JsonNode>map(mapper::valueToTree).orElse(NullNode.getInstance());
	}

	private JsonNode validate(JsonNode arguments) {
		workbench.java().validate(value(arguments, "installation", JavaInstallation.class),
				value(arguments, "requirement", JavaRequirement.class));
		return NullNode.getInstance();
	}

	/**
	 * Prepares a workspace. The engine filled text assets in already, so their text is used as it arrives.
	 */
	private JsonNode prepare(JsonNode arguments) {
		List<WorkspaceCache> defaults = new ArrayList<>();
		arguments.path("providerDefaults").forEach(cache -> defaults.add(mapper.convertValue(cache, WorkspaceCache.class)));
		WorkspaceRequest request = WorkspaceRequest.builder()
				.root(path(arguments, "root"))
				.directory(path(arguments, "directory"))
				.process(value(arguments, "process", MinecraftProcess.class))
				.plan(value(arguments, "plan", WorkspacePlan.class))
				.providerDefaults(defaults)
				.build();
		PreparedWorkspace prepared = workbench.workspaces().prepare(request);
		String handle = handles.register(owner, prepared, () -> prepared.finish(false));

		return mapper.createObjectNode().put("handle", handle).put("workspace", prepared.workspace().toString());
	}

	private JsonNode finishWorkspace(JsonNode arguments) {
		Object prepared = handles.remove(owner, arguments.path("handle").asText());
		if (prepared instanceof PreparedWorkspace workspace) workspace.finish(arguments.path("successful").asBoolean());
		return NullNode.getInstance();
	}

	private ProcessFiles files(JsonNode arguments) {
		return workbench.workspaces().files(path(arguments, "workspace"));
	}

	private JsonNode executions() {
		ArrayNode providers = mapper.createArrayNode();
		workbench.executions().forEach((id, provider) -> providers.addObject()
				.put("id", id)
				.put("preservesClientAddress", provider.preservesClientAddress())
				.put("connectsEnvironments", provider.connectsEnvironments()));
		return providers;
	}

	private JsonNode open(JsonNode arguments) {
		ExecutionProvider provider = workbench.executions().get(arguments.path("provider").asText());
		if (provider == null)
			throw new IllegalArgumentException("The workbench offers no execution provider '" + arguments.path("provider").asText() + "'");

		var context = workbench.context(value(arguments, "networkPolicy", NetworkPolicy.class)).toBuilder()
				.processPriority(ProcessPriority.valueOf(arguments.path("priority").asText(ProcessPriority.NORMAL.name())))
				.build();
		ExecutionSession session = provider.open(context);
		return mapper.getNodeFactory().textNode(handles.register(owner, session, session::close));
	}

	private JsonNode prepareTarget(JsonNode arguments) {
		ExecutionSession session = handles.get(owner, arguments.path("session").asText(), ExecutionSession.class);
		ProcessTarget target = session.prepare(value(arguments, "request", ProcessRequest.class));
		String handle = handles.register(owner, target, target::close);

		ObjectNode result = mapper.createObjectNode().put("handle", handle);
		result.set("address", mapper.valueToTree(target.address()));
		result.set("peerAddress", mapper.valueToTree(target.peerAddress()));
		result.put("bindAddress", target.bindAddress());
		try {
			result.set("agentAddress", mapper.valueToTree(target.agentAddress()));
			result.put("agentPort", target.agentPort());
			result.put("agentBindAddress", target.agentBindAddress());
		} catch (UnsupportedOperationException noAgent) {
			// A container runs no agent; the engine refuses agent requests for it as a local target would.
			result.put("noAgent", noAgent.getMessage());
		}
		result.set("listeners", mapper.valueToTree(target.listeners()));
		result.set("peerListeners", mapper.valueToTree(target.peerListeners()));
		return result;
	}

	private JsonNode start(JsonNode arguments) {
		ProcessTarget target = handles.get(owner, arguments.path("target").asText(), ProcessTarget.class);
		ProcessExecution execution = target.start(value(arguments, "launch", ProcessLaunch.class));
		return mapper.getNodeFactory().textNode(handles.register(owner, execution, () -> execution.terminate(true)));
	}

	private ProcessExecution execution(JsonNode arguments) {
		return handles.get(owner, arguments.path("handle").asText(), ProcessExecution.class);
	}

	private JsonNode executeIn(JsonNode arguments) {
		List<String> command = new ArrayList<>();
		arguments.path("command").forEach(part -> command.add(part.asText()));
		RunningContainer.CommandResult result = execution(arguments)
				.execute(Duration.ofMillis(arguments.path("timeoutMillis").asLong()), command.toArray(String[]::new));
		return mapper.createObjectNode().put("exitCode", result.exitCode()).put("output", result.output());
	}

	private JsonNode forget(JsonNode arguments) {
		handles.remove(owner, arguments.path("handle").asText());
		return NullNode.getInstance();
	}

	private <T extends AutoCloseable> JsonNode close(JsonNode arguments, Class<T> type) throws Exception {
		String handle = arguments.path("handle").asText();
		T value = handles.get(owner, handle, type);
		handles.remove(owner, handle);
		value.close();
		return NullNode.getInstance();
	}

	private <T> T value(JsonNode arguments, String member, Class<T> type) {
		return mapper.convertValue(arguments.path(member), type);
	}

	private <T> T optional(JsonNode arguments, String member, Class<T> type) {
		JsonNode node = arguments.path(member);
		return node.isMissingNode() || node.isNull() ? null : mapper.convertValue(node, type);
	}

	private static Path path(JsonNode arguments, String member) {
		return Path.of(arguments.path(member).asText());
	}

	private JsonNode text(Path path) {
		return mapper.getNodeFactory().textNode(path.toString());
	}

	private JsonNode nullable(Path path) {
		return path == null ? NullNode.getInstance() : text(path);
	}

	private static JsonNode run(Action action) throws Exception {
		action.run();
		return NullNode.getInstance();
	}

	@FunctionalInterface
	private interface Action {
		void run() throws Exception;
	}
}
