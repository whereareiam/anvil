---
title: Reporting issues
description: Produce a minimal reproducible report with version pins, scenario code, and relevant diagnostics.
---

Report framework defects through the [Anvil issue tracker](https://github.com/whereareiam/anvil/issues).
First isolate one failing test or manual scenario with its original distribution pins.

Include:

- Anvil version or commit, operating system, Gradle version, and the JVM running the build.
- Execution provider, server/proxy platforms, exact builds or checksums, and selected Java runtimes.
- Relevant `build.gradle.kts` configuration and a minimal scenario/test with its imports.
- The exact command you ran, expected observation, and actual result.
- The first exception and cause, suppressed cleanup errors, and relevant `anvil-console.log` tails.
- Whether the workspace was disposable or persistent and whether caches affect reproduction.

For a routing problem, include the declared entrypoint, proxy/backend names, native client version,
and the identity or backend observation that failed. For a packaged extension, include its artifact
dependencies and service descriptor names.

Remove account tokens, agent credentials, and private account-store contents from any attachment.
Share relevant text and a minimal fixture rather than uploading the entire shared cache. If a
reproducer requires online authentication, describe that requirement without providing the account.

The [troubleshooting guide](../troubleshooting/index.md) explains which diagnostics survive each
failure stage. Contributions that fix a defect should follow the [testing guide](../../contributing/testing/index.md).
