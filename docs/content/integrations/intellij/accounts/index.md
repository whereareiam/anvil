---
title: Project accounts
description: Add project accounts, choose global account availability, and manage account pools in IntelliJ IDEA.
---

Select **Anvil Accounts…** in the Anvil tool window toolbar, or find it with **Find Action**, to manage the
accounts available to the current IntelliJ project. The **Accounts** tab shows each account's profile names,
local ID and source (**Project** or **Global**). After the profile names, a tag such as **Java** or **Bedrock**
names each Minecraft edition the account is available for; an edition whose services refused the account, or that
no library has checked yet, has no tag. An account file that Anvil has not rewritten since it recorded results per
protocol library, including those of Anvil 1.0, still shows the profile names it recorded, but no tag until a
sign-in or a player that uses the account records the editions again. An account is one Microsoft account that
every library derives its own session from; see
[Authentication](../../../building-blocks/players/authentication/index.md). Account and pool operations are saved
immediately; **Close** finishes the manager.

## Add an account

Open the manager from the Scenarios tab with a scenario source selected, choose **+** and then
**Sign In with Browser…**, and enter a unique local ID.
Anvil prepares the selected scenario source's authentication runtime and displays progress in a sign-in
dialog. When prompted, select **Open sign-in page** or **Copy link**, then complete sign-in in your
browser. Instructions appear while authentication is running. **Cancel sign-in** stops the active
operation and cleans up its temporary launch manifest.

New accounts are saved in the project account directory. **+ → Import Account File…** adds an existing
Anvil JSON account file to that directory. Replacing an existing project account requires confirmation.
When the project has no accounts yet, the empty list offers both choices as links.

Select an account and choose **Export Account File…** to save it to a new or existing file. **−** removes
a project account after confirmation; global accounts cannot be removed here. Removing an account retains its
pool memberships so those references can be reviewed in the pool editor.

## Include global accounts

Whether this project uses the global accounts, and where both account directories are, is set on
**Settings → Tools → Anvil → Accounts**; the **Account directories…** link at the bottom of the manager
opens that page. See [account preferences](../settings/index.md#accounts).

Project and global accounts must use distinct local IDs when both sources are enabled. The manager
shows duplicate IDs as a problem; remove the duplicate project account or stop using global accounts
before running. Read errors are shown without exposing credential contents.

## Create and edit pools

On **Pools**, choose **+**, enter a name, and select its members with checkboxes; each account carries the
same edition tags as in the **Accounts** tab. **Save Pool** persists the selection. Select an existing pool and
choose the edit button to rename it or change membership. Cancelling the editor leaves that pool unchanged.
**−** deletes the selected project pool after confirmation and keeps the account files.

Saved members that are absent from the current account sources remain visible as **Not currently
available**. Restore their accounts, enable the required source, or deselect those members before
using the pool. Project and global pool files are combined for a run; duplicate pool names are
reported and must be resolved.
Pools are saved as `pools.properties` in each account directory, in the
[format that runs read](../../../building-blocks/players/authentication/index.md). Gradle runs read
only their configured account directory, the global one by default, so project pools are available
to runs started from the IDE.
