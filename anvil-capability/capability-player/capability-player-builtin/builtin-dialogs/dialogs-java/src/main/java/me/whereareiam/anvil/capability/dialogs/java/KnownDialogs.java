package me.whereareiam.anvil.capability.dialogs.java;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtCompound;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtInt;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * The built-in dialogs a vanilla client knows from its own data pack, as the dialog codec writes them, for the
 * registry entries a server sends without their data once the client named that pack among those it knows. Every
 * release from Minecraft 1.21.6, which introduced dialogs, until 26.3 has the same three: the server links, and the
 * custom options and quick actions, which list the dialogs of a tag.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class KnownDialogs {
	private static final int BUTTON_WIDTH = 310;
	private static final int COLUMNS = 1;
	private static final int EXIT_WIDTH = 200;

	private static final Map<String, NbtCompound> DIALOGS = Map.of(
			"minecraft:server_links", menu("minecraft:server_links", "menu.server_links"),
			"minecraft:custom_options", dialogList("menu.custom_options", "#minecraft:pause_screen_additions"),
			"minecraft:quick_actions", dialogList("menu.quick_actions", "#minecraft:quick_actions")
	);

	/**
	 * Finds the data of a built-in dialog.
	 *
	 * @param key the dialog's registry name, such as {@code minecraft:server_links}
	 * @return the dialog's data, or {@code null} when no built-in dialog has that name
	 */
	static @Nullable NbtCompound find(@NotNull String key) {
		return DIALOGS.get(key);
	}

	private static NbtCompound dialogList(String title, String dialogs) {
		return menu("minecraft:dialog_list", title).with("dialogs", new NbtString(dialogs));
	}

	/**
	 * A menu of the pause screen: one column of wide buttons, titled by its translation key, with a back button.
	 */
	private static NbtCompound menu(String type, String title) {
		return NbtCompound.builder()
				.put("type", new NbtString(type))
				.put("button_width", new NbtInt(BUTTON_WIDTH))
				.put("columns", new NbtInt(COLUMNS))
				.put("exit_action", NbtCompound.builder()
						.put("label", translated("gui.back"))
						.put("width", new NbtInt(EXIT_WIDTH))
						.build())
				.put("external_title", translated(title))
				.put("title", translated(title + ".title"))
				.build();
	}

	private static NbtCompound translated(String key) {
		return NbtCompound.EMPTY.with("translate", new NbtString(key));
	}
}
