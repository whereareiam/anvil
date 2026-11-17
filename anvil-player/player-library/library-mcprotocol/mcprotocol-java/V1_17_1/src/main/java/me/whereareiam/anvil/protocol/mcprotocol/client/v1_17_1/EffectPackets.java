package me.whereareiam.anvil.protocol.mcprotocol.client.v1_17_1;

import com.github.steveice10.mc.protocol.data.game.entity.Effect;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5.EffectTranslation;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * The effects of entities in the MCProtocolLib 1.17.1 release, whose library names each effect after its vanilla key,
 * such as {@code SPEED} for {@code minecraft:speed}, so the translation names an effect by the library's name.
 */
final class EffectPackets extends EffectTranslation {
	private static final String MINECRAFT = "minecraft:";

	@Override
	protected @NotNull String key(@NotNull Effect effect) {
		return MINECRAFT + effect.name().toLowerCase(Locale.ROOT);
	}
}
