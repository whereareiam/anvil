package me.whereareiam.anvil.environment.provisioning.workspace.snapshot;

import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.workspace.WorkspaceAsset;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Encodes process selection and ordered asset inputs for compatible workspace snapshot lookup.
 */
final class WorkspaceSnapshotIdentity {
	static @NotNull String of(@NotNull MinecraftProcess process, @NotNull List<WorkspaceAsset> assets) {
		Distribution distribution = process.getDistribution();
		String selector = process.getName() + "|" + process.getPlatform() + "|"
				+ Objects.toString(distribution.getVersion(), "") + "|" + Objects.toString(distribution.getBuild(), "") + "|"
				+ Objects.toString(distribution.getSha256(), "") + "|" + Objects.toString(distribution.getLocalJar(), "");

		return selector + "\nassets=" + assetFingerprint(assets);
	}

	static @NotNull String key(@NotNull String identity, @NotNull String key) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest((identity + "\n" + key).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException failure) {
			throw new IllegalStateException("SHA-256 is not available", failure);
		}
	}

	private static @NotNull String assetFingerprint(@NotNull List<WorkspaceAsset> assets) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			for (WorkspaceAsset asset : assets) {
				digest.update(asset.getGroup().getBytes(StandardCharsets.UTF_8));
				digest.update(asset.getTarget().toString().getBytes(StandardCharsets.UTF_8));
				digest.update(asset.getMode().name().getBytes(StandardCharsets.UTF_8));
				Path source = asset.getSource().getPath();
				if (source != null)
					fingerprint(source.toAbsolutePath().normalize(), digest);
			}

			return HexFormat.of().formatHex(digest.digest());
		} catch (NoSuchAlgorithmException failure) {
			throw new IllegalStateException("SHA-256 is not available", failure);
		}
	}

	private static void fingerprint(@NotNull Path source, @NotNull MessageDigest digest) {
		try {
			if (Files.isRegularFile(source)) {
				digest.update(source.toString().getBytes(StandardCharsets.UTF_8));
				try (var input = Files.newInputStream(source)) {
					byte[] buffer = new byte[8192];
					int read;
					while ((read = input.read(buffer)) >= 0)
						digest.update(buffer, 0, read);
				}
				return;
			}
			if (Files.isDirectory(source))
				try (var paths = Files.walk(source)) {
					for (Path path : paths.sorted().toList()) {
						digest.update(source.relativize(path).toString().getBytes(StandardCharsets.UTF_8));
						if (Files.isRegularFile(path))
							fingerprint(path, digest);
					}
				}
		} catch (IOException failure) {
			throw new ProvisioningException("Could not fingerprint workspace asset " + source, failure);
		}
	}
}
