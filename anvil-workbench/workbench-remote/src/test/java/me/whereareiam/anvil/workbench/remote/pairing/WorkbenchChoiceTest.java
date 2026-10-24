package me.whereareiam.anvil.workbench.remote.pairing;

import me.whereareiam.anvil.workbench.api.exception.WorkbenchException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class WorkbenchChoiceTest {
	@TempDir
	Path temporary;

	@Test
	void prefersTheOptionThenTheEnvironmentThenTheDefaultThenThisMachine() throws Exception {
		Pairings pairings = new Pairings(temporary);
		pairings.save(pairing("workstation"));
		pairings.save(pairing("server"));

		assertEquals(Optional.empty(), WorkbenchChoice.chosen(null, null, pairings));
		pairings.setDefault("server");
		assertEquals("server", WorkbenchChoice.chosen(null, null, pairings).orElseThrow().getName());
		assertEquals("workstation", WorkbenchChoice.chosen(null, "workstation", pairings).orElseThrow().getName());
		assertEquals("server", WorkbenchChoice.chosen("server", "workstation", pairings).orElseThrow().getName());
		assertEquals(Optional.empty(), WorkbenchChoice.chosen("local", "workstation", pairings));
	}

	@Test
	void refusesANameThatIsNotPaired() {
		WorkbenchException refused = assertThrows(WorkbenchException.class,
				() -> WorkbenchChoice.chosen("laptop", null, new Pairings(temporary)));
		assertTrue(refused.getMessage().contains("No workbench named 'laptop' is paired"), refused.getMessage());
	}

	@Test
	void keepsPairingsAndForgetsTheDefaultWithItsWorkbench() throws Exception {
		Pairings pairings = new Pairings(temporary);
		pairings.save(pairing("workstation"));
		pairings.setDefault("workstation");

		assertEquals(pairing("workstation"), new Pairings(temporary).find("workstation").orElseThrow());
		assertTrue(pairings.remove("workstation"));
		assertEquals(Optional.empty(), pairings.defaultName());
		assertThrows(IllegalArgumentException.class, () -> pairings.setDefault("workstation"));
	}

	private static Pairing pairing(String name) {
		return Pairing.builder().name(name).address("192.168.1.20:7400").fingerprint("ab".repeat(32)).token("cd".repeat(32)).build();
	}
}
