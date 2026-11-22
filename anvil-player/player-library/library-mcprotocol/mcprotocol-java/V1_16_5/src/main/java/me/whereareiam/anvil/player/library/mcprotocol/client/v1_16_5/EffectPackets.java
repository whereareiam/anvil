package me.whereareiam.anvil.player.library.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.data.game.entity.Effect;
import org.jetbrains.annotations.NotNull;

/**
 * The effects of entities in the MCProtocolLib 1.16.5 release, whose library names its effects after the fields that
 * held them before Minecraft 1.13, such as {@code FASTER_MOVEMENT} for speed, and keeps only their numbers, so the
 * translation names each of the library's effects by the vanilla key of its number.
 */
final class EffectPackets extends EffectTranslation {
	private static final String MINECRAFT = "minecraft:";

	@Override
	protected @NotNull String key(@NotNull Effect effect) {
		return MINECRAFT + switch (effect) {
			case FASTER_MOVEMENT -> "speed";
			case SLOWER_MOVEMENT -> "slowness";
			case FASTER_DIG -> "haste";
			case SLOWER_DIG -> "mining_fatigue";
			case INCREASE_DAMAGE -> "strength";
			case HEAL -> "instant_health";
			case HARM -> "instant_damage";
			case JUMP -> "jump_boost";
			case CONFUSION -> "nausea";
			case REGENERATION -> "regeneration";
			case RESISTANCE -> "resistance";
			case FIRE_RESISTANCE -> "fire_resistance";
			case WATER_BREATHING -> "water_breathing";
			case INVISIBILITY -> "invisibility";
			case BLINDNESS -> "blindness";
			case NIGHT_VISION -> "night_vision";
			case HUNGER -> "hunger";
			case WEAKNESS -> "weakness";
			case POISON -> "poison";
			case WITHER -> "wither";
			case HEALTH_BOOST -> "health_boost";
			case ABSORBTION -> "absorption";
			case SATURATION -> "saturation";
			case GLOWING -> "glowing";
			case LEVITATION -> "levitation";
			case LUCK -> "luck";
			case UNLUCK -> "unluck";
			case SLOW_FALLING -> "slow_falling";
			case CONDUIT_POWER -> "conduit_power";
			case DOLPHINS_GRACE -> "dolphins_grace";
			case BAD_OMEN -> "bad_omen";
			case HERO_OF_THE_VILLAGE -> "hero_of_the_village";
		};
	}
}
