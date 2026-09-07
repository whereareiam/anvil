---
title: Documentation
description: Write nested task-oriented pages and preview the Scriptorium consumer content.
---

Anvil provides content to Scriptorium through `scriptorium.project.json`, `docs/content`, and
`docs/assets`. The Scriptorium application lives in its own repository; Anvil does not need another
web application or dependency installation.

## Choose the page's home

| Reader task | Section |
|---|---|
| Add Anvil and run a first test | Getting started |
| Configure a test framework or build tool | Integrations |
| Prepare and control servers, proxies, files, and players | Building blocks |
| Automate a journey or prepare a scenario for human testing | Workflows |
| Implement public extension points | Extending Anvil |
| Look up exact options or coordinates | The relevant configuration or integration guide |
| Change Anvil itself | Contributing |
| Diagnose a problem | Help |

Keep test-framework integrations and build-tool integrations as siblings. JUnit lifecycle guidance
belongs under `integrations/junit`; Gradle dependency, source-set, and task wiring belongs under
`integrations/gradle`. Cross-link their shared workflow without making one the parent of the other.

Keep Environments and Players together under Building blocks: both supply resources used by either
workflow. Testing and Scenarios belong under Workflows; they describe automated verification and
human testing respectively. Integrations comes last under Using Anvil and supports both workflows.

Under Environments, Provisioning covers obtaining platform software, Java, and acquired-artifact
caching. Workspaces covers assets, persistence, cache snapshots, and cleanup. Keep the acquired
artifact cache distinct from workspace snapshots, even though they share a cache root.

All readers are developers. Group related tasks under a shared directory; give a coherent task its
own page. Use `index.md` or `index.mdx` as the page entry and `meta.json` to order meaningful child
groups. Avoid a page that merely repeats a neighboring guide's option table.

## Author a page

Every page has `title` and `description` frontmatter:

```markdown
---
title: Restarting a process
description: Replace a running server or proxy and verify its application state.
---

Write the guide body here. Scriptorium displays the frontmatter title above it.
```

Start with the outcome and prerequisites. Show file placement, dependencies, a complete example or
explicitly scoped fragment, the invocation, and what success proves. Check public packages and
symbols against source. Explain failure symptoms beside the operation that can fail.

Use relative content links including the `.md` or `.mdx` extension. Reuse Scriptorium steps, tabs,
callouts, and partials when they clarify a choice or avoid actual repetition. Shared MDX partials
belong under `_partials`. Keep copyable code free of Markdown link syntax inside URLs.

Document current behavior in present tense. Keep implementation architecture in contributor pages
and migration narratives in release or migration material. Do not describe a fixture backend as a
production integration or claim application persistence from a successful reconnect.

## Maintain navigation and metadata

Use descriptive root sidebar headings to separate reader tasks. Preserve useful headings when
adjusting one grouping; avoid combined labels for unrelated tasks or a catch-all `Project` heading.
Keep option tables and API links beside the guides that use them rather than collecting them in a
separate Reference section. When a heading already names a section, inline that folder's ordered
contents with a metadata entry such as `...extending`. This keeps the pages under the heading
without repeating its name as a single expandable folder; content paths and URLs stay the same.

Each `meta.json` supplies a descriptive section title and ordered page names. When a group needs a
landing page, name it `Overview` in its frontmatter, so the sidebar shows `Configuration → Overview` rather
than repeating `Configuration` twice. Individual task pages keep their descriptive titles. Add
`meta.json` to groups with child pages; a single-page folder does not need a navigation file.
Order pages by the work a reader performs: prerequisites before their consumers, a runnable common
path before optional variants, and advanced provider integrations after the basic extension example.
Keep a section's introductory reading path in the same order as its sidebar. Detailed option tables
and help are lookup material, not prerequisites for completing the first test. Keep entries aligned with the
filesystem and link to child tasks from useful landing pages. Source pages belong under
`docs/content`; do not add loose developer notes under `docs`.

`scriptorium.project.json` defines branding, repository metadata, and version policy. The configured
home version is `dev`; included release branches and tags must contain the consumer documentation
contract. Runtime source credentials belong to the Scriptorium deployment, not project metadata.
Generated content and local runtime output belong in ignored `.scriptorium` directories.

## Preview and verify

In a local Scriptorium checkout, configure `app/scriptorium.json` to target the Anvil repository:

```json
{
  "source": {
    "type": "local",
    "target": "/absolute/path/to/Anvil"
  }
}
```

Run `bun install` and `bun run dev` from that Scriptorium checkout. Its local source adapter watches
the consumer files and compiles the content. Preserve other users' preview configuration when
checking a different project.

Validate frontmatter, ordered navigation, relative links and anchors, code fences, and Scriptorium
compilation. Review the rendered sidebar and a representative guide at every nested level. Compile
substantial Java examples where practical; successful Markdown compilation only verifies the page,
not the Java program. Documentation-only changes do not require running the Minecraft matrix.
