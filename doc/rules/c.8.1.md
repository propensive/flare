# `↯/c.8.1` — Top-level import rules

**Principle:** `↯/c.8` — Locatability

Raised by flare when a top-level import
violates the project's import-style rules.

## Cause

Consequent scans top-level imports and enforces three rules: no aliases,
ordering by group, and group separation by blank lines. Violations
are emitted with rule tags `9.1`, `9.2`, and `9.3`.

## Variants

### `↯/c.8.1.1` — No aliases

Top-level imports must not use `as` or `=>` aliases — write the full
path.

**Example.** `[↯/f.21.1/c.8.1.1] top-level imports must not use aliases (`as` or `=>`); write the full path`

**Resolution.** Remove the alias and refer to the full path
throughout the file.

### `↯/c.8.1.2` — Group order

Imports must be in the documented group order, and within a group
must appear in alphabetical order.

**Example.** `[↯/f.21.1/c.8.1.2] import group 3 appears after group 4`

**Example.** `[↯/f.21.1/c.8.1.2] import `alpha` is out of alphabetical order (after `vacuous`)`

**Resolution.** Reorder imports by group and alphabetically.

### `↯/c.8.1.3` — Group separation

Import groups must be separated by exactly one blank line; no blank
line is permitted within a group.

**Example.** `[↯/f.21.1/c.8.1.3] import groups must be separated by exactly one blank line`

**Example.** `[↯/f.21.1/c.8.1.3] unexpected blank line within an import group`

**Resolution.** Add or remove the blank line as required.

## See also

- [The standard](../consequent-style.md)
- Source: `src/plugin/flare.FrameRules.scala`
