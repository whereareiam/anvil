package me.whereareiam.anvil.api.model.scenario;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * An ordered collection of scenarios exposed under one manual runner group.
 */
@Value
@Builder
public class ScenarioGroup {
	@NotNull String name;

	@NotNull
	@Singular
	List<String> scenarios;
}
