---
title: Writing documentation
description: Author and preview task-oriented Anvil documentation with Scriptorium.
---

# Writing documentation

Anvil supplies documentation through Scriptorium's consumer layout. The application is maintained separately;
this repository supplies content and project metadata, not a second Next.js installation.

```text
scriptorium.project.json
docs/
  assets/
  content/
    meta.json
    index.md
    getting-started/index.md
    writing-tests/
    running-environments/
    extending/
    contributing/
```

## Pages and navigation

Pages use Markdown or MDX with `title` and `description` frontmatter. Place a topic's landing page
at `index.md` or `index.mdx`, and use `meta.json` to order the pages in a section. The root navigation
is organized by what someone wants to do: getting started, writing tests, running environments,
extending Anvil, or contributing to its implementation. All readers are developers; do not use a
generic developer section to mix public extension guides with repository-maintenance instructions.

Link to another source page with a relative path, including its extension. Keep
links inside `docs/content` for content that needs to work in the deployed site; repository source
examples can use explicit GitHub URLs. A shared MDX partial belongs under `_partials` and can be
included with Scriptorium's `Include` component when real reuse justifies it.

Use present-tense explanations and copyable examples. Verify public type names, packages, Gradle
tasks, supported versions, and pins against current source. Label fragments that depend on existing
variables, and distinguish contract fixtures from production implementations.

## Project metadata and versions

`scriptorium.project.json` declares the project name, branding asset, repository link, and version
policy. The home version is `dev`; `release/*` branches and tags are included when they contain the
documentation contract. Runtime source/webhook credentials belong to the Scriptorium deployment,
not this file.

The small SVG in `docs/assets` supplies the required project logo and favicon. Generated bundles,
search indexes, and local runtime state belong in ignored `.scriptorium/` output.

## Local preview

In a local Scriptorium checkout, configure its `app/scriptorium.json` to target this repository:

```json
{
  "source": {
    "type": "local",
    "target": "/absolute/path/to/Anvil"
  }
}
```

Run `bun install` and `bun run dev` from the Scriptorium checkout. Its local source adapter watches
the consumer contract and builds the page tree, compiled content, and search data. Do not overwrite
someone else's preview configuration when checking another project.

Validate frontmatter, navigation entries, local links, and content compilation after changes.
Check code fences as well as prose: Markdown link syntax must never be embedded inside a Gradle
repository URL. Documentation-only changes do not require restarting the live Minecraft matrix.
