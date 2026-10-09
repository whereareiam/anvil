package me.whereareiam.anvil.protocol.mcprotocol.segment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SegmentLinkageTest {
	private static final String TARGET = Target.class.getName();
	private static final String CONTRACT = Contract.class.getName();
	private static final AtomicInteger INITIALIZATIONS = new AtomicInteger();

	@Test
	void resolvesClassesAndDeclaredOrInheritedMembersWithTheirRequirementsWithoutInitializingThem() {
		SegmentLinkage linkage = read("""
				class %1$s public class
				field %1$s inherited:I instance
				field %1$s CONSTANT:Ljava/lang/String; static
				field %1$s value:Ljava/lang/String; instance
				field %1$s exposed:Ljava/lang/String; public instance
				method %1$s <init>(I)V instance class
				method %1$s inheritedMethod()V instance class
				method %1$s staticMethod(Ljava/lang/String;)Ljava/lang/String; static class
				method %1$s privateMethod()J instance class
				method %1$s guarded()V protected instance class
				method %1$s visible()V public instance class
				method %1$s hashCode()I public instance class
				class %2$s public interface
				method %2$s act()V public instance interface
				method java.lang.Runnable run()V public instance interface
				""".formatted(TARGET, CONTRACT));

		assertEquals(Optional.empty(), linkage.firstFailure(getClass().getClassLoader()));
		assertEquals(0, INITIALIZATIONS.get());
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"class example.MissingClass | missing class example.MissingClass",
			"field %1$s absent:I | missing field %1$s absent:I",
			"field %1$s inherited:J | missing field %1$s inherited:J",
			"method %1$s absent()V | missing method %1$s absent()V",
			"method %1$s inheritedMethod()I | missing method %1$s inheritedMethod()I",
			"method %1$s <init>()V | missing method %1$s <init>()V",
			"method %1$s missingType(Lexample/MissingClass;)V | missing method %1$s missingType(Lexample/MissingClass;)V",
			"class %2$s class | %2$s is an interface, linked as a class",
			"method %1$s visible()V instance interface | %1$s is a class, linked as an interface",
			"method %1$s staticMethod(Ljava/lang/String;)Ljava/lang/String; instance | method %1$s staticMethod(Ljava/lang/String;)Ljava/lang/String; is static, linked as instance",
			"field %1$s value:Ljava/lang/String; static | field %1$s value:Ljava/lang/String; is not static, linked as static",
			"method %1$s guarded()V public instance | method %1$s guarded()V is no longer public",
			"method %1$s privateMethod()J protected instance | method %1$s privateMethod()J is no longer protected",
			"class %3$s public | class %3$s is no longer public"
	})
	void reportsWhyTheFirstFailingReferenceDoesNotLink(String reference, String failure) {
		String hidden = Hidden.class.getName();
		SegmentLinkage linkage = read("class " + TARGET + "\n" + reference.formatted(TARGET, CONTRACT, hidden) + "\nclass example.Later\n");

		assertEquals(Optional.of(failure.formatted(TARGET, CONTRACT, hidden)), linkage.firstFailure(getClass().getClassLoader()));
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"type example.Type", "class", "method example.Type", "method example.Type run", "field example.Type value",
			"field example.Type :I"
	})
	void rejectsLinesThatAreNotReferences(String line) {
		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> read(line));
		assertEquals("'" + line + "' in test linkage is not a linkage reference", failure.getMessage());
	}

	@Test
	void rejectsUnknownRequirements() {
		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> read("field example.Type a:I volatile"));
		assertEquals("'field example.Type a:I volatile' in test linkage has an unknown requirement 'volatile'", failure.getMessage());
	}

	private SegmentLinkage read(String text) {
		return SegmentLinkage.read(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), "test linkage");
	}

	@SuppressWarnings("unused")
	public static class Base {
		int inherited;

		void inheritedMethod() {
		}

		static String staticMethod(String value) {
			return value;
		}
	}

	@SuppressWarnings("unused")
	public static final class Target extends Base {
		static final String CONSTANT = String.valueOf(INITIALIZATIONS.incrementAndGet());

		public String exposed;
		private String value;

		Target(int value) {
		}

		private long privateMethod() {
			return 0;
		}

		protected void guarded() {
		}

		public void visible() {
		}
	}

	@SuppressWarnings("unused")
	public interface Contract {
		void act();
	}

	@SuppressWarnings("unused")
	static final class Hidden {
	}
}
