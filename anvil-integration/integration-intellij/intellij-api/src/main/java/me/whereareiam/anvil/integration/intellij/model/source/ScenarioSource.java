package me.whereareiam.anvil.integration.intellij.model.source;

import com.intellij.openapi.actionSystem.DataKey;

import java.nio.file.Path;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.integration.intellij.source.BuildIntegration;
import org.jetbrains.annotations.NotNull;

/**
 * Imported project source that exposes Anvil scenario tooling.
 * <p>
 * A build integration discovers sources from the IDE's imported project model. The identifier is stable
 * across imports and is what saved selections, such as run configurations, store.
 */
@Value
@Builder(toBuilder = true)
public class ScenarioSource {
	/**
	 * The source a UI component is showing, offered to actions through the IDE's data context.
	 * <p>
	 * Features contribute actions to each other's toolbars and menus through action groups; this key is how
	 * such an action learns which source it applies to without depending on the component that shows it.
	 */
	public static final DataKey<ScenarioSource> DATA_KEY = DataKey.create("anvil.scenarioSource");

	/**
	 * Stable identity, unique within the IntelliJ project.
	 */
	@NotNull String id;
	/**
	 * Label shown to users, such as the build's project path.
	 */
	@NotNull String displayName;
	/**
	 * Identifier of the {@code BuildIntegration} that discovered this source and prepares it.
	 */
	@NotNull String integrationId;
	/**
	 * Directory of the module that declares the scenarios.
	 */
	@NotNull Path directory;
	/**
	 * Increases whenever the IDE imports the source's project again, so observers can tell an import apart
	 * from an unchanged rediscovery.
	 */
	long importRevision;
}
