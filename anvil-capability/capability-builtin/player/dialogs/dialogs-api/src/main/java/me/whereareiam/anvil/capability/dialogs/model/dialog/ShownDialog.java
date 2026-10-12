package me.whereareiam.anvil.capability.dialogs.model.dialog;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * A dialog found by a wait, with the history position directly after it.
 */
@Value
public class ShownDialog {
	@NotNull Dialog dialog;
	/**
	 * Checkpoint directly after this dialog; pass it to the next wait to look only at later dialogs.
	 */
	int checkpoint;
}
