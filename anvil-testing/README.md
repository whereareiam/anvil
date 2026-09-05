# Anvil's cross-module tests

Choose the module by what the test exercises:

| Module | Responsibility | Sources |
|---|---|---|
| `testing-runtime` | Provider discovery, selection, and capability composition without starting Minecraft | `src/test/java` |
| `testing-server` | Real servers/proxies: player sessions, capabilities, routing, and external agent operations | `src/test/java` |
| `testing-fixtures/fixtures-server-plugin` | Plugin and native agent operations installed into test servers | `src/main/java`, `src/main/resources` |
| `testing-fixtures/fixtures-external-extension` | External backend/capability/agent example, plus shared JAR/class-loader test setup | `src/main/java`, `src/main/resources` |

Tests owned by one production module remain in that module. These modules cover behavior that
crosses production-module boundaries. Fixtures remain case-specific unless several tests reuse
them or a real plugin/extension JAR is needed.

## Runtime checks

The `testing-runtime` provider package contains `PlatformProviderDiscoveryIntegrationTest` and
`ExternalProviderDiscoveryIntegrationTest`. They verify installed providers, automatic/explicit
selection rules, missing-capability diagnostics, and offline authentication support. These tests
run with ordinary `test` and `build`.

```shell
./gradlew :anvil-testing:testing-runtime:test
```

## Real-server tests

`testing-server` groups tests by behavior:

- `session/PlayerIdentityReconnectSystemTest`: kicks, reconnects, and identity replacement.
- `capability/PlayerCapabilitiesSystemTest`: protocol capability actions observed by a real server.
- `routing/ProxyServerCompatibilitySystemTest`: direct and proxy routes across the supported versions.
- `extension/ExternalExtensionSystemTest`: an external backend and capability adapter calling a real agent.
- `scenario/`: the shared compatibility catalog and pinned Paper scenario definitions.

The whole test task requires full mode. There are no mixed live/non-live methods selected by tags.

```shell
./gradlew :anvil-testing:testing-server:test -Panvil.testMode=full
./gradlew :anvil-testing:testing-server:test -Panvil.testMode=full --tests '*ExternalExtensionSystemTest'
./gradlew :anvil-testing:testing-server:test -Panvil.testMode=full --tests '*ProxyServerCompatibilitySystemTest' -PanvilMatrixFilter='.*spigot.*'
```

`anvilMatrixFilter` affects compatibility cases only. Use `--tests` to select a test class. The root
`test -Panvil.testMode=full` command runs the complete framework matrix. Consumer journeys run
separately after local publication with `./gradlew -p examples/proof-of-patience anvilTest`.

The native matrix covers 1.18.2, 1.19.4, 1.20.6, 1.21.11, 26.1.2, and 26.2: 36 routes plus six
capability and six identity/reconnect cases. Framework startup and each binding's exact library are
also checked by `:anvil-protocol:protocol-mcprotocol:mcprotocol-client:test`.

## Fixture artifacts

Fixture modules produce ordinary JARs and are not published. `fixtures-server-plugin` owns the
test server's commands and native observations. `fixtures-external-extension` compiles against
public Anvil APIs only; its in-process backend is a conformance example, not a second Minecraft
packet implementation.

`ExternalExtensionFixture` owns temporary JAR packaging and scoped class loading shared by runtime
and server tests. Case-specific broken/missing dependencies remain part of the external-extension
fixture. Its host setup code is excluded from the JAR installed in a platform agent.

The server-plugin JAR is resolved through a Gradle artifact configuration. Tests do not guess
fixture paths or versions in another module's build directory. Diagnostic workspaces are retained
under `testing-server/build/anvil` when a live run fails. Old workspaces under the grouping module's
`build` directory are not deleted by this reorganization.
