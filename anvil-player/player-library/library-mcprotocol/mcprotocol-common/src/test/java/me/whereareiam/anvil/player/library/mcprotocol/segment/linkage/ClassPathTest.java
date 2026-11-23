package me.whereareiam.anvil.player.library.mcprotocol.segment.linkage;

import me.whereareiam.anvil.player.library.mcprotocol.segment.linkage.LinkageReference.Kind;
import me.whereareiam.anvil.player.library.mcprotocol.segment.linkage.LinkageReference.Requirement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassPathTest {
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
	void linksEveryReferenceOnTheReleaseTheSegmentCompiledAgainst() throws IOException {
		try (ClassPath release = fixture.classPath(fixture.firstRelease())) {
			assertEquals(List.of(), failures(release));
		}
	}

	@Test
	void reportsMissingMembersStaticAndInterfaceChangesAndNarrowerAccessOnALaterRelease() throws IOException {
		try (ClassPath release = fixture.classPath(fixture.laterRelease())) {
			assertEquals(
					List.of(
							"method lib.Api count()I is not static, linked as static",
							"method lib.Api narrowed()V is no longer public",
							"missing method lib.Api removed()V",
							"lib.Handler is a class, linked as an interface"
					),
					failures(release)
			);
			assertEquals("missing class lib.Gone", release.linkageFailure(reference(Kind.CLASS, "lib/Gone", "")));
		}
	}

	@Test
	void reportsAnInterfaceLinkedAsAClassAStaticMemberLinkedAsInstanceAndAProtectedMemberMadePrivate() throws IOException {
		Path privateClasses = fixture.compile("private", List.of(), "package lib; public class Base { private void helper() {} }");
		try (ClassPath first = fixture.classPath(fixture.firstRelease()); ClassPath privateRelease = fixture.classPath(privateClasses)) {
			assertEquals("lib.Listener is an interface, linked as a class",
					first.linkageFailure(reference(Kind.CLASS, "lib/Listener", "", Requirement.CLASS)));
			assertEquals("method lib.Api count()I is static, linked as instance",
					first.linkageFailure(reference(Kind.METHOD, "lib/Api", "count()I", Requirement.INSTANCE)));
			assertEquals("method lib.Base helper()V is no longer protected",
					privateRelease.linkageFailure(reference(Kind.METHOD, "lib/Base", "helper()V", Requirement.PROTECTED, Requirement.INSTANCE)));
		}
	}

	@Test
	void resolvesMembersInheritedFromLibraryAndJdkSupertypes() throws IOException {
		try (ClassPath release = fixture.classPath(fixture.laterRelease())) {
			assertEquals("lib/Base", release.declaringClass(reference(Kind.METHOD, "lib/Api", "inherited()V")));
			assertEquals("java/lang/Object", release.declaringClass(reference(Kind.METHOD, "lib/Api", "hashCode()I")));
			assertNull(release.linkageFailure(reference(Kind.METHOD, "lib/Api", "toString()Ljava/lang/String;")));
			assertEquals("missing field lib.Api NAME:Ljava/lang/String;",
					release.linkageFailure(reference(Kind.FIELD, "lib/Api", "NAME:Ljava/lang/String;")));
		}
	}

	@Test
	void resolvesAConstructorOnlyOnTheClassItNames() throws IOException {
		try (ClassPath release = fixture.classPath(fixture.laterRelease())) {
			assertEquals("lib/Api", release.declaringClass(reference(Kind.METHOD, "lib/Api", "<init>()V")));
			assertEquals("missing method lib.Api <init>(Ljava/lang/String;)V",
					release.linkageFailure(reference(Kind.METHOD, "lib/Api", "<init>(Ljava/lang/String;)V", Requirement.INSTANCE)));
			assertNull(release.declaringClass(reference(Kind.METHOD, "lib/Listener", "<init>()V")));
		}
	}

	@Test
	void readsJdkClassesFromEveryModuleOfTheGivenInstallation() {
		JdkClasses jdk = fixture.jdk();

		assertTrue(jdk.contains("java/lang/String"));
		assertTrue(jdk.contains("java/sql/Connection"));
		assertTrue(Objects.requireNonNull(jdk.header("java/util/List")).isInterface());
		assertFalse(jdk.contains("lib/Api"));
		assertFalse(jdk.contains("Unnamed"));
	}

	@Test
	void listsTheClassesOfItsRootsWithoutTheJdk() throws IOException {
		try (ClassPath release = fixture.classPath(fixture.laterRelease(), directory.resolve("missing"))) {
			assertEquals(Set.of("lib/Api", "lib/Base", "lib/Handler", "lib/Listener"), release.classNames());
			assertTrue(release.contains("lib/Api"));
			assertFalse(release.contains("java/lang/Object"));
		}
	}

	@Test
	void namesAClassFileNewerThanTheLinkageCheckReads() throws IOException {
		Path classes = directory.resolve("future");
		byte[] api = Files.readAllBytes(fixture.firstRelease().resolve("lib/Api.class"));
		api[6] = 0;
		api[7] = 99;
		Files.createDirectories(classes.resolve("lib"));
		Files.write(classes.resolve("lib/Api.class"), api);

		try (ClassPath future = fixture.classPath(classes)) {
			IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
					() -> future.linkageFailure(reference(Kind.CLASS, "lib/Api", "")));

			assertTrue(failure.getMessage().contains("lib/Api.class has class file version 99 (Java 55)"), failure.getMessage());
			assertTrue(failure.getMessage().contains("update 'asm' in gradle/libs.versions.toml"), failure.getMessage());
		}
	}

	private List<String> failures(ClassPath release) {
		return fixture.externalReferences().stream().map(release::linkageFailure).filter(Objects::nonNull).toList();
	}

	private static LinkageReference reference(Kind kind, String owner, String member, Requirement... requirements) {
		return new LinkageReference(kind, owner, member, Set.of(requirements));
	}
}
