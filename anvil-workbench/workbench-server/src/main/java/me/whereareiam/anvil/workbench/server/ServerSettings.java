package me.whereareiam.anvil.workbench.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.workbench.api.model.WorkbenchFacts;
import me.whereareiam.anvil.workbench.protocol.WireProtocol;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * How a workbench's operator set it up: where it keeps its data, the address it listens on, its name, and what it
 * allows the engines that use it. Read from {@code workbench.toml} in the data directory:
 *
 * <pre>{@code
 * name = "workstation"
 * port = 7400
 *
 * [policy]
 * execution = ["docker"]          # "docker", "local", or both
 * reaches = ["*.mojang.com"]      # hosts processes may contact; "*" is every host
 * processors = 8                  # most one engine's scenario may use; 0 is the machine's
 * memoryMegabytes = 16384
 * }</pre>
 */
@Value
@Builder(toBuilder = true)
public class ServerSettings {
	/**
	 * Execution provider that runs processes as containers.
	 */
	public static final String DOCKER = "docker";
	/**
	 * Execution provider that runs processes directly on the machine.
	 */
	public static final String LOCAL = "local";

	@NotNull Path dataDirectory;
	@NotNull String name;
	@NotNull String bindAddress;
	int port;

	/**
	 * Execution providers engines may use, in order of preference.
	 */
	@NotNull
	@Singular("execution")
	List<String> executions;

	/**
	 * Hosts processes may contact; {@code *} is every host.
	 */
	@NotNull
	@Singular("reach")
	List<String> reaches;

	/**
	 * Processors a scenario may use; 0 is the machine's.
	 */
	int processors;

	/**
	 * Memory a scenario may use in MiB; 0 is the machine's.
	 */
	int memoryMegabytes;

	/**
	 * Reads the settings of a data directory, which need not hold a {@code workbench.toml}.
	 *
	 * @param dataDirectory workbench data directory
	 * @return settings with every omitted member at its default
	 * @throws IOException when the file cannot be read
	 */
	public static @NotNull ServerSettings read(@NotNull Path dataDirectory) throws IOException {
		Path file = dataDirectory.resolve("workbench.toml");
		JsonNode root = Files.exists(file) ? new TomlMapper().readTree(file.toFile()) : new TomlMapper().createObjectNode();
		JsonNode policy = root.path("policy");

		var settings = builder()
				.dataDirectory(dataDirectory.toAbsolutePath().normalize())
				.name(root.path("name").asText(hostName()))
				.bindAddress(root.path("bind").asText("0.0.0.0"))
				.port(root.path("port").asInt(WireProtocol.DEFAULT_PORT))
				.processors(policy.path("processors").asInt(0))
				.memoryMegabytes(policy.path("memoryMegabytes").asInt(0));
		List<String> executions = strings(policy.path("execution"));
		settings.executions(executions.isEmpty() ? List.of(DOCKER) : executions);
		List<String> reaches = strings(policy.path("reaches"));
		settings.reaches(reaches.isEmpty() ? List.of("*") : reaches);

		return settings.build();
	}

	/**
	 * Narrows a workbench's facts to what this policy allows engines.
	 *
	 * @param facts facts of the machine
	 * @return facts under this policy, named after this workbench
	 */
	public @NotNull WorkbenchFacts restrict(@NotNull WorkbenchFacts facts) {
		return facts.toBuilder()
				.workbenchName(name)
				.local(false)
				.processors(processors > 0 ? Math.min(processors, facts.getProcessors()) : facts.getProcessors())
				.memoryMegabytes(memoryMegabytes > 0 ? Math.min(memoryMegabytes, facts.getMemoryMegabytes()) : facts.getMemoryMegabytes())
				.clearReachableHosts()
				.reachableHosts(reaches)
				.build();
	}

	private static List<String> strings(JsonNode node) {
		List<String> values = new ArrayList<>();
		if (node.isTextual()) values.add(node.asText());
		node.forEach(value -> values.add(value.asText()));
		return values;
	}

	private static String hostName() {
		try {
			return InetAddress.getLocalHost().getHostName();
		} catch (IOException unknown) {
			return "workbench";
		}
	}
}
