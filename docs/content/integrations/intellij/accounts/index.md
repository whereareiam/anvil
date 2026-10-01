---
title: Project accounts
description: Add project accounts, choose global account availability, and manage account pools in IntelliJ IDEA.
---

Select **Accounts…** in the Anvil tool window toolbar to manage accounts available to the current
IntelliJ project. The **Accounts** tab shows each account's name, local ID, source (**Project** or
**Global**), and provider. Account and pool operations are saved immediately; **Close** finishes the manager.

## Add an account

Select a scenario source in Scenarios, then choose **Add account…** and enter a unique local ID.
Anvil prepares the selected scenario source's authentication runtime and displays progress in a sign-in
dialog. When prompted, select **Open sign-in page** or **Copy link**, then complete sign-in in your
browser. Instructions appear while authentication is running. **Cancel sign-in** stops the active
operation and cleans up its temporary launch manifest.

New accounts are saved in the project account directory. **Import…** adds an existing Anvil JSON
account file to that directory. Replacing an existing project account requires confirmation.

Select an account and choose **Export…** to save it to a new or existing file using the save dialog. **Remove** is
available only for project accounts and asks for confirmation. Removing an account retains its
pool memberships so those references can be reviewed in the pool editor.

## Include global accounts

**Include global accounts in this project** takes effect immediately and refreshes the account list.
It controls this project's access to the global account directory configured in
[preferences](../settings/index.md).
It does not change the global directory or make new accounts global.

Project and global accounts must use distinct local IDs when both sources are enabled. The manager
shows duplicate IDs as a problem; remove the duplicate project account or disable global accounts
before running. Read errors are shown without exposing credential contents.

## Create and edit pools

On **Pools**, choose **Create pool…**, enter a name, and select its members with checkboxes. **Save Pool**
persists the selection. Select an existing pool and choose **Edit…** to rename it or change membership.
Cancelling the editor leaves that pool unchanged. **Remove** deletes the selected project pool after
confirmation and keeps the account files.

Saved members that are absent from the current account sources remain visible as **Not currently
available**. Restore their accounts, enable the required source, or deselect those members before
using the pool. Project and global pool files are combined for a run; duplicate pool names are
reported and must be resolved.
Pools are saved as `pools.properties` in each account directory, in the
[format that runs read](../../../building-blocks/players/authentication/index.md). Gradle runs read
only their configured account directory, the global one by default, so project pools are available
to runs started from the IDE.

## Change project storage

Expand **Storage** to change the project account directory. By default each project keeps its accounts
in its own directory under `~/.anvil/projects`, outside the project tree, so saved sign-ins cannot be
committed with the source. Relative paths are resolved from the IntelliJ project directory; avoid
choosing a directory inside the project unless your version control ignores it. Choose **Use Default**
to fill in the default path, then **Apply Location** to save the change and refresh accounts and pools.
The chosen location is a personal setting stored in the IDE workspace, not in shared project files.

Editing the path alone does not change the active location. Applying a location affects subsequent
imports and sign-ins and does not move existing account files. Unapplied path edits are discarded
when the manager closes.
