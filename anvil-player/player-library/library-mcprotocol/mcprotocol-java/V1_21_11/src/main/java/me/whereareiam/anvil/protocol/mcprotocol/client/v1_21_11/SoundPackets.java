package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.protocol.java.api.type.SoundSource;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.SoundTranslation;
import org.geysermc.mcprotocollib.protocol.data.game.level.sound.SoundCategory;
import org.jetbrains.annotations.NotNull;

/**
 * Sounds of the MCProtocolLib 1.21.11 release, whose categories the library names mostly in the singular and which
 * adds the user interface category.
 */
public final class SoundPackets extends SoundTranslation {
	@Override
	protected @NotNull SoundSource source(@NotNull SoundCategory category) {
		return switch (category) {
			case MASTER -> SoundSource.MASTER;
			case MUSIC -> SoundSource.MUSIC;
			case RECORD -> SoundSource.RECORDS;
			case WEATHER -> SoundSource.WEATHER;
			case BLOCK -> SoundSource.BLOCKS;
			case HOSTILE -> SoundSource.HOSTILE;
			case NEUTRAL -> SoundSource.NEUTRAL;
			case PLAYER -> SoundSource.PLAYERS;
			case AMBIENT -> SoundSource.AMBIENT;
			case VOICE -> SoundSource.VOICE;
			case UI -> SoundSource.UI;
		};
	}
}
