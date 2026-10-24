package me.whereareiam.anvil.workbench.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import me.whereareiam.anvil.workbench.protocol.CertificateFingerprint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * What makes a workbench itself: the certificate engines pin, the tokens of the engines it paired with, and the
 * one-time code an engine pairs with. Everything lives in the workbench's data directory; a token is kept only as
 * its SHA-256, so the directory leaks no credential.
 */
final class ServerIdentity {
	private static final String KEYSTORE = "identity.p12";
	private static final String ALIAS = "workbench";
	private static final String ENGINES = "engines.json";
	private static final String PAIRING = "pairing.json";
	private static final Duration PAIRING_VALIDITY = Duration.ofMinutes(15);
	private static final SecureRandom RANDOM = new SecureRandom();

	private final @NotNull Path directory;
	private final @NotNull ObjectMapper mapper;
	private final @NotNull SSLContext tls;
	private final @NotNull String fingerprint;

	private ServerIdentity(@NotNull Path directory, @NotNull ObjectMapper mapper, @NotNull SSLContext tls, @NotNull String fingerprint) {
		this.directory = directory;
		this.mapper = mapper;
		this.tls = tls;
		this.fingerprint = fingerprint;
	}

	/**
	 * Opens the identity in a data directory, creating its certificate with the JDK's {@code keytool} the first
	 * time.
	 *
	 * @param directory workbench data directory
	 * @param mapper JSON mapper
	 * @param name workbench name, which the certificate names
	 * @return the identity
	 * @throws IOException when the certificate cannot be created or read
	 */
	static @NotNull ServerIdentity open(@NotNull Path directory, @NotNull ObjectMapper mapper, @NotNull String name) throws IOException {
		Files.createDirectories(directory);
		Path keystore = directory.resolve(KEYSTORE);
		char[] password = keystorePassword(directory);
		if (!Files.exists(keystore)) generate(keystore, password, name);

		try (InputStream input = Files.newInputStream(keystore)) {
			KeyStore store = KeyStore.getInstance("PKCS12");
			store.load(input, password);
			KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			keys.init(store, password);
			SSLContext tls = SSLContext.getInstance("TLS");
			tls.init(keys.getKeyManagers(), null, RANDOM);
			String fingerprint = CertificateFingerprint.of((X509Certificate) store.getCertificate(ALIAS));

			return new ServerIdentity(directory, mapper, tls, fingerprint);
		} catch (GeneralSecurityException failure) {
			throw new IOException("Could not read the workbench certificate " + keystore, failure);
		}
	}

	/**
	 * Returns the TLS context engines connect through.
	 *
	 * @return server TLS context
	 */
	@NotNull SSLContext tls() {
		return tls;
	}

	/**
	 * Returns the fingerprint engines pin.
	 *
	 * @return certificate fingerprint
	 */
	@NotNull String fingerprint() {
		return fingerprint;
	}

	/**
	 * Returns whether the workbench has paired with any engine yet.
	 *
	 * @return true when at least one engine holds a token
	 * @throws IOException when the engines cannot be read
	 */
	synchronized boolean paired() throws IOException {
		return engines().size() > 0;
	}

	/**
	 * Creates a new one-time pairing code, replacing an earlier unused one.
	 *
	 * @return the code
	 * @throws IOException when the code cannot be written
	 */
	synchronized @NotNull String newPairingCode() throws IOException {
		String code = HexFormat.of().formatHex(bytes(5)).toUpperCase(Locale.ROOT);
		ObjectNode pairing = mapper.createObjectNode()
				.put("code", hash(code))
				.put("expires", Instant.now().plus(PAIRING_VALIDITY).toString());
		write(directory.resolve(PAIRING), pairing);

		return code;
	}

	/**
	 * Pairs an engine that presents the current pairing code, which then stops working.
	 *
	 * @param code code the engine presented
	 * @param engine name the engine gave itself, for the operator
	 * @return the engine's new token, or null when the code is wrong or expired
	 * @throws IOException when the identity cannot be written
	 */
	synchronized @Nullable String pair(@NotNull String code, @NotNull String engine) throws IOException {
		Path file = directory.resolve(PAIRING);
		if (!Files.exists(file)) return null;

		ObjectNode pairing = (ObjectNode) mapper.readTree(file.toFile());
		if (Instant.parse(pairing.path("expires").asText()).isBefore(Instant.now())) return null;
		if (!MessageDigest.isEqual(hash(code.trim().toUpperCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8),
				pairing.path("code").asText().getBytes(StandardCharsets.UTF_8)))
			return null;

		Files.delete(file);
		String token = HexFormat.of().formatHex(bytes(32));
		ObjectNode engines = engines();
		engines.putObject(hash(token)).put("engine", engine).put("paired", Instant.now().toString());
		write(directory.resolve(ENGINES), engines);

		return token;
	}

	/**
	 * Returns the name of the engine a token belongs to.
	 *
	 * @param token token the engine presented
	 * @return engine name, or null when no engine holds the token
	 * @throws IOException when the engines cannot be read
	 */
	synchronized @Nullable String engine(@Nullable String token) throws IOException {
		if (token == null || token.isBlank()) return null;

		var entry = engines().get(hash(token));
		return entry == null ? null : entry.path("engine").asText();
	}

	/**
	 * Lists the names of the paired engines.
	 *
	 * @return engine names
	 * @throws IOException when the engines cannot be read
	 */
	synchronized @NotNull List<String> engineNames() throws IOException {
		return engines().properties().stream().map(entry -> entry.getValue().path("engine").asText()).toList();
	}

	private ObjectNode engines() throws IOException {
		Path file = directory.resolve(ENGINES);
		return Files.exists(file) ? (ObjectNode) mapper.readTree(file.toFile()) : mapper.createObjectNode();
	}

	private void write(Path file, ObjectNode content) throws IOException {
		Path staged = file.resolveSibling(file.getFileName() + ".tmp");
		mapper.writerWithDefaultPrettyPrinter().writeValue(staged.toFile(), content);
		ownerOnly(staged);
		Files.move(staged, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}

	private static char[] keystorePassword(Path directory) throws IOException {
		Path file = directory.resolve("identity.secret");
		if (!Files.exists(file)) {
			Files.writeString(file, HexFormat.of().formatHex(bytes(24)));
			ownerOnly(file);
		}

		return Files.readString(file).trim().toCharArray();
	}

	private static void generate(Path keystore, char[] password, String name) throws IOException {
		Path keytool = Path.of(System.getProperty("java.home"), "bin",
				System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "keytool.exe" : "keytool");
		Process process = new ProcessBuilder(keytool.toString(), "-genkeypair", "-alias", ALIAS, "-keyalg", "EC",
				"-groupname", "secp256r1", "-validity", "36500", "-dname", "CN=Anvil workbench " + name.replace(',', ' '),
				"-storetype", "PKCS12", "-keystore", keystore.toString(), "-storepass", new String(password),
				"-keypass", new String(password))
				.redirectErrorStream(true).start();
		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		try {
			if (process.waitFor() != 0)
				throw new IOException("keytool could not create the workbench certificate: " + output.trim());
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while creating the workbench certificate", interrupted);
		}
	}

	private static void ownerOnly(Path file) throws IOException {
		try {
			Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
		} catch (UnsupportedOperationException notPosix) {
			// Windows keeps the data directory to its user through the directory's own permissions.
		}
	}

	private static byte[] bytes(int count) {
		byte[] bytes = new byte[count];
		RANDOM.nextBytes(bytes);
		return bytes;
	}

	private static String hash(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (GeneralSecurityException failure) {
			throw new IllegalStateException("SHA-256 is not available", failure);
		}
	}
}
