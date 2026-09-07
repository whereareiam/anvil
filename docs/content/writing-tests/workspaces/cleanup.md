---
title: Workspace cleanup
description: Control when workspace files are removed and when diagnostics remain available.
---

# Workspace cleanup

Cleanup rules decide when Anvil deletes or preserves a path owned by the current process. The
engine evaluates them separately from asset installation and cache restoration.

Use `BEFORE_START` for files that must disappear before a process launches, `AFTER_STOP` for files
that should be removed after a successful run, and `ON_FAILURE` for diagnostics that should remain
when a scenario fails.

```java
import java.nio.file.Path;

import me.whereareiam.anvil.api.model.workspace.WorkspaceCleanup;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import me.whereareiam.anvil.api.type.CleanupPhase;

WorkspacePlan plan = WorkspacePlan.builder()
        .cleanup(WorkspaceCleanup.builder()
                .path(Path.of("logs"))
                .phase(CleanupPhase.ON_FAILURE)
                .build())
        .build();
```

Do not place failure diagnostics inside a path that another cleanup rule removes. Workspace caches
persist only when the scenario completes successfully, while failed runs keep the retained
workspace for inspection.
