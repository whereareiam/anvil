package me.whereareiam.anvil.capability.dialogs.screen;

import me.whereareiam.anvil.capability.dialogs.model.dialog.Dialog;
import me.whereareiam.anvil.capability.dialogs.model.dialog.RegistryDialog;
import me.whereareiam.anvil.capability.dialogs.type.DialogAfterAction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DialogRegistryTest {
	private static final Dialog RULES = Dialog.builder()
			.type("minecraft:notice")
			.title("Rules")
			.externalTitle("Rules")
			.closeWithEscape(true)
			.afterAction(DialogAfterAction.CLOSE)
			.build();
	private static final DialogRegistry REGISTRY = new DialogRegistry(List.of(
			new RegistryDialog("minecraft:server_links", null),
			new RegistryDialog("example:rules", RULES)
	));

	@Test
	void resolvesARegistryIdToTheDialogTheServerSent() {
		assertEquals(RULES.toBuilder().registryId(1).registryName("example:rules").build(), REGISTRY.resolve(reference(1)));
	}

	@Test
	void namesADialogTheServerLeftOutAndKeepsAnUnknownReference() {
		assertEquals(reference(0).toBuilder().registryName("minecraft:server_links").build(), REGISTRY.resolve(reference(0)));
		assertEquals(reference(7), REGISTRY.resolve(reference(7)));
	}

	@Test
	void findsDialogsByNameOnlyWhenTheServerSentThem() {
		assertEquals(RULES.toBuilder().registryId(1).registryName("example:rules").build(), REGISTRY.find("example:rules"));
		assertNull(REGISTRY.find("minecraft:server_links"));
		assertNull(REGISTRY.find("example:missing"));
	}

	private static Dialog reference(int id) {
		return Dialog.builder()
				.type("")
				.registryId(id)
				.title("")
				.externalTitle("")
				.afterAction(DialogAfterAction.CLOSE)
				.build();
	}
}
