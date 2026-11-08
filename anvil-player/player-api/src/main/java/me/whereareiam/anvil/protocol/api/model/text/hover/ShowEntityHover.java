package me.whereareiam.anvil.protocol.api.model.text.hover;

import lombok.Value;
import me.whereareiam.anvil.protocol.api.model.text.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Shows an entity's type, identity and name.
 */
@Value
public class ShowEntityHover implements TextHover {
	/**
	 * The entity type's key, such as {@code minecraft:zombie}.
	 */
	@NotNull String type;
	/**
	 * The entity's identity.
	 */
	@NotNull UUID uniqueId;
	/**
	 * The entity's name, or {@code null}.
	 */
	@Nullable Text name;
}
