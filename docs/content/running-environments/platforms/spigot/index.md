---
title: Prebuilt Spigot
description: Download GetBukkit Spigot releases with explicit content pins and no local BuildTools run.
---

# Prebuilt Spigot

Anvil downloads prebuilt Spigot JARs from the third-party
[GetBukkit supplier](https://getbukkit.org/download/spigot). It does not run BuildTools or compile
Spigot locally. Local and named executable artifacts remain supported.

GetBukkit addresses releases by version rather than immutable build number. Pin the expected
executable bytes with `Distribution.pinned(version, sha256)`:

```java
MinecraftServer.builder()
        .name("server")
        .platform(Platforms.SPIGOT)
        .distribution(Distribution.pinned(
                "1.21.11",
                "6481503fca2838776b3da5a3f1c030e1328abc2fd77d9ea1bb4814889b540dcd"
        ))
        .build();
```

## Verified content pins

These hashes identify JARs retrieved from GetBukkit on 2026-09-05 and used by Anvil's compatibility
catalog. They are recorded content pins, not claims of upstream-signed releases.

| Minecraft version | SHA-256 |
|---|---|
| `1.18.2` | `6996f8025b497dd32271b09b6624b26b95d0e39dc9f6860d01fd0db38905076d` |
| `1.19.4` | `beb43043e8d03d1d4557755102aebc0bfe89368a594939fb1db5f9e5ee84ebb3` |
| `1.20.6` | `d9aab418f8404148b47c8faba484698a526f121f9fb5412264de9dc4b65db02f` |
| `1.21.11` | `6481503fca2838776b3da5a3f1c030e1328abc2fd77d9ea1bb4814889b540dcd` |
| `26.1.2` | `95f871fd6d055ba10b5a058768ddad43b0be0286480c8eba435c835c95d5f19c` |
| `26.2` | `b9295ba3fd4c9d75e361122ff89b7978b2afbe5538e60d7fa5cd7dcd96a355b1` |

Spigot 1.18.2 permits Java 17–18 and 1.19.4 permits Java 17–20. Anvil selects a JVM within those
bounds and supplies Java-17-compatible Bukkit agents. Plugins under test must target a compatible
Java release as well. Do not add flags that bypass Spigot's Java-version validation.

The provider downloads `https://cdn.getbukkit.org/spigot/spigot-<version>.jar` and verifies its
SHA-256 before accepting it. If supplier bytes change, a fresh download fails until you deliberately
select and verify another pin. Anvil never replaces a checksum automatically or falls back to a
local build.

## Cache and migration

Artifacts are stored under
`~/.anvil/distributions/getbukkit/spigot/<version>/<sha256>/spigot-<version>.jar` by default.
The checksum is part of the path, keeping distinct pinned artifacts separate. Existing BuildTools
cache files are untouched and are not reused by the supplier.

Replace old Spigot `Distribution.remote(version, build)` declarations with
`Distribution.pinned(version, sha256)`. Remote Spigot selection requires an exact numeric version
and a 64-digit SHA-256, including for manual scenarios. BuildTools build IDs and `latest` are rejected.

For a JAR from your own registry, use `Distribution.artifact(name)` and declare `minecraftVersion`.
The artifact registry resolves its file before platform provisioning.
