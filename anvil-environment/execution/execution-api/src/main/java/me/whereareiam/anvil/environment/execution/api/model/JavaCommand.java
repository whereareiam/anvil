package me.whereareiam.anvil.environment.execution.api.model;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import lombok.Singular;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Execution-neutral Java command with a host JAR and explicit argument boundaries.
 */
@Value
@Builder(toBuilder = true)
public class JavaCommand {
	@NotNull Path jar;
	int memoryMegabytes;
	@NotNull
	@Singular("jvmArgument")
	List<String> jvmArguments;
	@NotNull
	@Singular("argument")
	List<String> arguments;
	@NotNull
	@Singular("environmentVariable")
	Map<String, String> environment;
}
