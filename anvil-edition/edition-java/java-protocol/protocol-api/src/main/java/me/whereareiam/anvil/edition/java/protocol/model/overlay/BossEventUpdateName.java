package me.whereareiam.anvil.edition.java.protocol.model.overlay;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * Changes a boss bar's text.
 */
@Value
public class BossEventUpdateName implements BossEventOperation {
	/**
	 * The bar's text as plain text.
	 */
	@NotNull String name;
}
