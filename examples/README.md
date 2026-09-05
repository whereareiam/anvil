# Proof of Patience

This is a tiny authentication plugin with a revolutionary security model: Alice runs `/auth`, gets
kicked, is rejected twice, reconnects a third time, and is declared trustworthy. Credentials are
complicated. Counting to three is enterprise-ready.

The example is a standalone consumer build. It applies the published `me.whereareiam.anvil` plugin
and selects `me.whereareiam.anvil:default`; it is intentionally not a subproject of Anvil's
framework build.

The project is shaped like an actual plugin rather than a museum exhibit assembled from several
Gradle modules:

```text
proof-of-patience/
└── src/
    ├── main/    the plugin users would install
    ├── test/    fast tests for the reconnect rule
    └── anvil/   real Paper scenarios and player journeys
```

Run the ordinary test:

```shell
./gradlew -p examples/proof-of-patience test
```

When working from an unpublished checkout, run `./gradlew publishToMavenLocal` first. To test another
Anvil version, pass the same `-PanvilVersion=<version>` to publication and example commands.

Run the journey that normally consumes a developer, two terminals, and whatever remained of the
afternoon:

```shell
./gradlew -p examples/proof-of-patience anvilTest
```

Alice waits for Paper, records her original UUID, starts the challenge, verifies every kick,
reconnects three times, and checks the UUID observed by the server. The same journey runs against
Minecraft `1.21.11` and `26.1.2`.

Do not install this plugin on a real network. Patience is a virtue, not an authentication factor.
