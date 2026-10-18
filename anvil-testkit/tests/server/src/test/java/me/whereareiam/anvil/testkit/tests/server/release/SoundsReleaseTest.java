package me.whereareiam.anvil.testkit.tests.server.release;

import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.capability.sounds.SoundWatch;
import me.whereareiam.anvil.capability.sounds.Sounds;
import me.whereareiam.anvil.capability.sounds.model.HeardSound;
import me.whereareiam.anvil.capability.sounds.type.SoundCategory;
import me.whereareiam.anvil.testkit.tests.server.fixture.FixtureServer;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Hears the sounds the server plays where the player stands: the console plays a sound at the world spawn, which a
 * player may stand too far from to hear it.
 */
@Release
class SoundsReleaseTest {
	@Test
	void hearsABuiltInSoundTheServerNamesByItsRegistryId(ScenarioContext anvil) {
		FixtureServer server = new FixtureServer(anvil);
		SimulatedPlayer player = server.join();
		SoundWatch sounds = player.capability(Sounds.class).watch();

		server.console("execute at " + player.name() + " run playsound minecraft:entity.experience_orb.pickup player "
				+ player.name());

		HeardSound heard = sounds.heard("entity.experience_orb.pickup", Duration.ofSeconds(10));
		assertEquals(SoundCategory.PLAYERS, heard.getCategory());
	}

	@Test
	void hearsASoundTheServerNames(ScenarioContext anvil) {
		FixtureServer server = new FixtureServer(anvil);
		SimulatedPlayer player = server.join();
		SoundWatch sounds = player.capability(Sounds.class).watch();

		server.console("execute at " + player.name() + " run playsound anvil:announcement master " + player.name());

		assertEquals(SoundCategory.MASTER, sounds.heard("anvil:announcement", Duration.ofSeconds(10)).getCategory());
	}
}
