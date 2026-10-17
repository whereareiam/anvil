package me.whereareiam.anvil.testkit.fixtures.bukkit.v1_21_1;

import me.whereareiam.anvil.testkit.fixtures.bukkit.FixtureCookies;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;

/**
 * Stores and asks for cookies through the API of Paper 1.21.1, which later releases keep.
 */
public final class PaperFixtureCookies implements FixtureCookies {
	@Override
	public void store(Player player, String key, String value) {
		player.storeCookie(namespaced(key), value.getBytes(StandardCharsets.UTF_8));
		player.sendMessage("anvil:cookie-stored:" + key);
	}

	@Override
	public void request(Player player, String key) {
		player.retrieveCookie(namespaced(key)).whenComplete((payload, failure) -> {
			if (failure != null) {
				player.sendMessage("anvil:cookie:" + key + ":failed");
				return;
			}

			String value = payload == null ? "none" : new String(payload, StandardCharsets.UTF_8);
			player.sendMessage("anvil:cookie:" + key + ":" + value);
		});
	}

	private static NamespacedKey namespaced(String key) {
		NamespacedKey namespaced = NamespacedKey.fromString(key);
		if (namespaced == null) throw new IllegalArgumentException("Not a cookie key: " + key);

		return namespaced;
	}
}
