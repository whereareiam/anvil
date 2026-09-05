package me.whereareiam.anvil.runner.model.command;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.runner.type.RunnerCommandType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * One parsed command from the interactive foreground session.
 */
@Value
@Builder
public class RunnerCommand {
	@NotNull RunnerCommandType type;
	@NotNull String token;

	@NotNull
	@Singular("argument")
	List<String> arguments;
}
