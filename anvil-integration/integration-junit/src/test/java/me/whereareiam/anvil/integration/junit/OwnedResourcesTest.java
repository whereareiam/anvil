package me.whereareiam.anvil.integration.junit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OwnedResourcesTest {
	@Test
	void findsAnOwnedObjectByAnyTypeItIsAnInstanceOf() {
		OwnedResources resources = new OwnedResources();
		StringBuilder text = resources.own(new StringBuilder("stub"));

		assertSame(text, resources.find(StringBuilder.class));
		assertSame(text, resources.find(CharSequence.class));
		assertNull(resources.find(Integer.class));
	}

	@Test
	void refusesATypeThatSeveralOwnedObjectsShare() {
		OwnedResources resources = new OwnedResources();
		resources.own(new StringBuilder("first"));
		resources.own("second");

		assertThrows(ExtensionConfigurationException.class, () -> resources.find(CharSequence.class));
		assertEquals("second", resources.find(String.class));
	}

	@Test
	void closesCloseableObjectsInReverseOrderAndKeepsTheFirstFailure() {
		List<String> closed = new ArrayList<>();
		RuntimeException first = new IllegalStateException("second resource");
		Exception later = new Exception("first resource");
		OwnedResources resources = new OwnedResources();
		resources.own((AutoCloseable) () -> { closed.add("first"); throw later; });
		resources.own("not closeable");
		resources.own((AutoCloseable) () -> { closed.add("second"); throw first; });

		assertSame(first, assertThrows(RuntimeException.class, resources::close));
		assertEquals(List.of("second", "first"), closed);
		assertSame(later, first.getSuppressed()[0].getCause());

		resources.close();
		assertEquals(List.of("second", "first"), closed);
	}
}
