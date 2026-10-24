package me.whereareiam.anvil.platform.api.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.process.ProcessFiles;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Map;

/**
 * Workspace locations and execution-selected endpoints available during platform preparation.
 * Artifact acquisition and provider instances remain bound to the preparing service.
 */
@Value
@Builder
public class PlatformRequest {
	@NotNull AnvilScenario scenario;
	@NotNull Path workDirectory;

	/**
	 * The files of the process's workspace, through which its configuration is written.
	 */
	@NotNull ProcessFiles files;

	/**
	 * Cache root on the workbench that runs the process, below which providers place obtained artifacts.
	 */
	@NotNull Path cacheDirectory;

	@NotNull Path workspaceGroupDirectory;
	@NotNull String bindAddress;
	int port;
	@NotNull
	@Singular("processAddress")
	Map<String, InetSocketAddress> processAddresses;
}
