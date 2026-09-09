# Anvil testkit

Choose the module by what the test exercises:

| Module                        | Responsibility                                                                              | Sources                               |
|-------------------------------|---------------------------------------------------------------------------------------------|---------------------------------------|
| `tests/runtime`               | Provider discovery, selection, and capability composition without starting Minecraft        | `src/test/java`                       |
| `tests/server`                | Real servers/proxies: player sessions, capabilities, routing, and external agent operations | `src/test/java`                       |
| `fixtures/test-process`       | Small executable used to observe process startup and lifecycle                              | `src/main/java`                       |
| `fixtures/test-server-plugin` | Plugin and native agent operations installed into test servers                              | `src/main/java`, `src/main/resources` |
| `fixtures/test-extension`     | External backend, capabilities, and agent handlers loaded from real JARs                    | `src/main/java`, `src/main/resources` |
| `support`                     | Artifact access and extension classloader lifetime for host-side tests                      | `src/main/java`                       |

Tests owned by one production module remain in that module. These modules cover behavior that
crosses production-module boundaries. Fixtures remain case-specific unless several tests reuse
them or a real plugin/extension JAR is needed.

## Runtime checks

The `tests/runtime` provider package contains `PlatformProviderDiscoveryIntegrationTest` and
`ExternalProviderDiscoveryIntegrationTest`. They verify installed providers, automatic/explicit
selection rules, missing-capability diagnostics, and offline authentication support. These tests
run with ordinary `test` and `build`.

```shell
./gradlew :anvil-testkit:tests:runtime:test
```

## Real-server tests

`tests/server` groups tests by behavior:

- `session/PlayerIdentityReconnectSystemTest`: kicks, reconnects, and identity replacement.
- `capability/PlayerCapabilitiesSystemTest`: protocol capability actions observed by a real server.
- `routing/ProxyServerCompatibilitySystemTest`: direct and proxy routes across the supported versions.
- `extension/ExternalExtensionSystemTest`: an external backend and capability adapter calling a real agent.
- `scenario/`: the shared compatibility catalog and pinned Paper scenario definitions.

The whole test task requires full mode. There are no mixed live/non-live methods selected by tags.

```shell
./gradlew :anvil-testkit:tests:server:test -Panvil.testMode=full
./gradlew :anvil-testkit:tests:server:test -Panvil.testMode=full --tests '*ExternalExtensionSystemTest'
./gradlew :anvil-testkit:tests:server:test -Panvil.testMode=full --tests '*ProxyServerCompatibilitySystemTest' -PanvilMatrixFilter='.*spigot.*'
```

`anvilMatrixFilter` affects compatibility cases only. Use `--tests` to select a test class. The root
`test -Panvil.testMode=full` command continues to run all live coverage, including consumer journeys.

## Fixture artifacts

Fixture modules produce ordinary JARs and are not published. `test-server-plugin` owns the
test server's commands and native observations. `test-extension` compiles against
public Anvil APIs only; its in-process backend is a conformance example, not a second Minecraft
packet implementation.

Gradle builds normal, broken-provider, and observation-only extension variants. Test tasks declare
which artifacts they need; shared build configuration resolves the exact JARs, builds them first,
and supplies their paths as tracked test inputs. `TestExtensionLoader` loads a prepared extension
JAR, isolates discovery, and closes its classloader. It does not construct or rewrite JARs.

Fixture producers form the independent `anvil-test-fixtures` consumer build. Their Anvil dependency
aliases resolve public artifact coordinates; the root composite substitutes current source projects.
Build the fixtures from source with:

```shell
./gradlew :anvil-test-fixtures:build
```

After publishing Anvil locally, check those public dependencies independently:

```shell
./gradlew -p anvil-testkit/fixtures clean build -PanvilVersion=0.0.1
```

Use the version you published. Fixture artifacts themselves are not published.

Tests do not guess fixture paths or versions in another project's build directory. Diagnostic
workspaces are retained under `tests/server/build/anvil` when a live run fails.
