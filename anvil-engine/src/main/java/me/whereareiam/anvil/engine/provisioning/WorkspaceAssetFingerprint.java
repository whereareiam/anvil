package me.whereareiam.anvil.engine.provisioning;

import me.whereareiam.anvil.api.model.workspace.WorkspaceAsset;
import me.whereareiam.anvil.engine.AnvilException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Computes the asset identity used to isolate compatible workspace cache snapshots.
 */
final class WorkspaceAssetFingerprint {
	String compute(List<WorkspaceAsset> assets) {
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
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is not available", e);
		}
	}

	private void fingerprint(Path source, MessageDigest digest) {
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
		} catch (IOException e) {
			throw new AnvilException("Could not fingerprint workspace asset " + source, e);
		}
	}

}
