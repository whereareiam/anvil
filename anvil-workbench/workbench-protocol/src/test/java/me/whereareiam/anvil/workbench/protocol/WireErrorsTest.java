package me.whereareiam.anvil.workbench.protocol;

import me.whereareiam.anvil.api.exception.JavaVersionMismatchException;
import me.whereareiam.anvil.api.exception.ProcessException;
import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.api.exception.scenario.ScenarioRequirementsException;
import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import me.whereareiam.anvil.workbench.api.exception.WorkbenchException;
import me.whereareiam.anvil.workbench.protocol.type.WireFailure;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WireErrorsTest {
	@Test
	void restoresEachKindAsTheExceptionAnEngineSeesLocally() {
		assertEquals(NoSuchFileException.class, roundTrip(new NoSuchFileException("server.properties")).getClass());
		assertEquals(IOException.class, roundTrip(new AccessDeniedException("plugins")).getClass());
		assertEquals(IOException.class, roundTrip(new UncheckedIOException(new IOException("disk full"))).getClass());
		assertEquals(IllegalArgumentException.class, roundTrip(new IllegalArgumentException("no such handle")).getClass());
		assertEquals(IllegalStateException.class, roundTrip(new IllegalStateException("closed")).getClass());
		assertEquals(UnsupportedOperationException.class, roundTrip(new UnsupportedOperationException("no agent")).getClass());
		assertEquals(ScenarioValidationException.class, roundTrip(new ScenarioValidationException("bad name")).getClass());
		assertEquals(ProvisioningException.class, roundTrip(new ProvisioningException("download failed")).getClass());
		assertEquals(WorkbenchException.class, roundTrip(new ArithmeticException("overflow")).getClass());
	}

	@Test
	void keepsWhatEachKindCarries() {
		NoSuchFileException missing = (NoSuchFileException) roundTrip(new NoSuchFileException("config/paper-global.yml"));
		assertEquals("config/paper-global.yml", missing.getFile());

		ProcessException process = (ProcessException) roundTrip(new ProcessException("lobby", "exited before becoming ready"));
		assertEquals("lobby", process.getProcessName());
		assertEquals("exited before becoming ready", process.getMessage());

		ScenarioRequirementsException refused = (ScenarioRequirementsException) roundTrip(
				new ScenarioRequirementsException("network", "workstation", List.of("its processes may not reach api.mojang.com")));
		assertEquals("network", refused.getScenarioName());
		assertEquals("workstation", refused.getWorkbenchName());
		assertEquals(List.of("its processes may not reach api.mojang.com"), refused.getUnmet());
	}

	@Test
	void namesAFailureByItsMostSpecificKind() {
		assertEquals(WireFailure.MISSING_FILE, WireErrors.describe(new NoSuchFileException("x")).getKind());
		assertEquals(WireFailure.PROVISIONING, WireErrors.describe(new JavaVersionMismatchException("Java 21 required, 17 found")).getKind());
	}

	private static Exception roundTrip(Exception failure) {
		return WireErrors.restore(WireErrors.describe(failure));
	}
}
