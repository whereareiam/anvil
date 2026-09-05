package me.whereareiam.anvil.protocol.mcprotocol.catalog;

import me.whereareiam.anvil.protocol.api.type.ProtocolCapability;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolCatalogTest {
	@Test
	void selectsPinnedDefinitionsAndReportsUnsupportedVersions() {
		ProtocolCatalog catalog = new ProtocolCatalog();

		assertEquals(774, catalog.require("1.21.11").getSupport().getProtocolNumber());
		assertEquals(775, catalog.require("26.1.2").getSupport().getProtocolNumber());
		assertTrue(catalog.require("26.1.2").getSupport().getCapabilities()
				.contains(ProtocolCapability.ONLINE_AUTHENTICATION));
		assertThrows(IllegalArgumentException.class, () -> catalog.require("latest"));
		assertThrows(UnsupportedOperationException.class, () -> catalog.definitions().clear());
	}
}
