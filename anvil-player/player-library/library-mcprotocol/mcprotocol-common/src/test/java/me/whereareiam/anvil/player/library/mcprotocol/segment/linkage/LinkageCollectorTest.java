package me.whereareiam.anvil.player.library.mcprotocol.segment.linkage;

import me.whereareiam.anvil.player.library.mcprotocol.segment.linkage.LinkageReference.Kind;
import me.whereareiam.anvil.player.library.mcprotocol.segment.linkage.LinkageReference.Requirement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkageCollectorTest {
	@TempDir
	Path directory;

	private LinkageFixture fixture;

	@BeforeEach
	void compileFixture() {
		fixture = new LinkageFixture(directory);
	}

	@AfterEach
	void closeFixture() throws IOException {
		fixture.close();
	}

	@Test
	void collectsLibraryReferencesWithTheirRequirementsWithoutTheJdkOwnClassesOrProjectDependencies() {
		List<String> references = fixture.externalReferences().stream().map(LinkageReference::line).sorted().toList();

		assertEquals(
				List.of(
						"class lib.Api public class",
						"class lib.Base public class",
						"class lib.Handler public",
						"class lib.Listener public interface",
						"field lib.Api version:Ljava/lang/String; public static",
						"method lib.Api <init>()V public instance class",
						"method lib.Api count()I public static class",
						"method lib.Api inherited()V public instance class",
						"method lib.Api kept()V public instance class",
						"method lib.Api narrowed()V public instance class",
						"method lib.Api removed()V public instance class",
						"method lib.Base <init>()V public instance class",
						"method lib.Base helper()V protected instance",
						"method lib.Handler handle()V public instance interface",
						"method lib.Listener onEvent()V public instance"
				),
				references
		);
	}

	@Test
	void attributesAnInheritedMemberUsedThroughAnOwnClassToTheLibraryClassDeclaringIt() {
		Set<LinkageReference> references = fixture.externalReferences();

		assertTrue(references.contains(new LinkageReference(Kind.METHOD, "lib/Listener", "onEvent()V",
				Set.of(Requirement.PUBLIC, Requirement.INSTANCE))));
		assertTrue(references.contains(new LinkageReference(Kind.METHOD, "lib/Base", "helper()V",
				Set.of(Requirement.PROTECTED, Requirement.INSTANCE))));
	}

	@Test
	void manifestLinesReadBackAsTheSameReferences() {
		Set<LinkageReference> references = fixture.externalReferences();

		assertEquals(references, references.stream()
				.map(reference -> LinkageReference.parse(reference.line(), "test manifest"))
				.collect(Collectors.toSet()));
	}

	@Test
	void rejectsUnknownRequirements() {
		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
				() -> LinkageReference.parse("method lib.Api kept()V public final", "test manifest"));

		assertTrue(failure.getMessage().contains("unknown requirement 'final'"), failure.getMessage());
	}
}
