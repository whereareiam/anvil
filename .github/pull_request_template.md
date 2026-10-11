## Summary

<!-- What changes for projects that test with Anvil, and why. -->

## Release notes

<!-- The title and the label decide what the release draft shows. See "Release notes" in AGENTS.md. -->

- [ ] The title follows `Area: Description` with a listed area
- [ ] The title describes the effect in the past tense, for example `Players: Fixed ...`
- [ ] Exactly one label: `feature`, `change`, `bug`, `dependencies`, or `skip-changelog`
- [ ] `bug` only for a bug that shipped in a release; a fix for a bug that only existed on `dev` is `skip-changelog`
- [ ] `major` added when projects must change their tests, build scripts or extensions on upgrade
- [ ] Upgrade steps added to `.github/release-notes.md`, if projects need them

## Checks

- [ ] Tests added or updated
- [ ] Documentation under `docs/content/` updated for changed behavior, DSL, tasks or versions
