---
title: Accounts
description: Require stored online accounts for a JUnit test and skip it on machines that have none.
---

A test that signs in with a real Minecraft account can only run where such an account is stored.
Declare the requirement with `@AnvilAccounts` so the test is skipped elsewhere, such as in CI, instead
of failing. See [Authentication](../../../building-blocks/players/authentication/index.md) for storing an account.

## Require accounts by count

The requirement counts accounts; it does not name one. The test then runs with whichever accounts the
developer has signed in, whatever their local IDs are. Request an `AccountPool` parameter and lease
from it:

```java
@AnvilTest(LobbyScenario.class)
@AnvilAccounts(2)
void tradesBetweenTwoAccounts(ScenarioContext anvil, AccountPool accounts) {
	SimulatedPlayer seller = anvil.players().create("seller", accounts.lease());
	SimulatedPlayer buyer = anvil.players().create("buyer", accounts.lease());
}
```

`@AnvilAccounts` without a value requires one account. An annotation on the method replaces the one
on its class. A lease reserves its account for the whole engine, and the pool releases unclaimed
leases when the test ends.

To create the player with options of your own, for example
[authentication on request](../../../building-blocks/players/authentication/index.md), pass the options
together with the lease. The leased account supplies the account ID and protocol library:

```java
SimulatedPlayer player = anvil.players().create(PlayerOptions.builder()
		.name("premium")
		.authentication(AuthenticationMode.ON_REQUEST)
		.build(), accounts.lease());
```

## Restrict the accounts to a pool

`@AnvilAccounts(value = 2, pool = "testers")` leases only from the pool named `testers` in the account
directory's `pools.properties` file. Use a pool when a test must not touch a developer's personal
account. The test is skipped when the machine does not declare the pool, or the pool lists an account
that is not stored.

## When the test is skipped

Anvil checks the requirement after the scenario is prepared and before any process starts, so a
skipped test launches no server. It is reported as aborted with the number of accounts that were
required and available. An account ID that several installed protocol libraries store cannot be
pooled and does not count.
