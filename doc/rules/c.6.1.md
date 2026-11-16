# `↯/c.6.1` — Spacing around commas

**Principle:** `↯/c.6` — Proximity

Raised by flare when whitespace around a comma
violates the project's style.

## Cause

Consequent requires that commas be flush with the preceding token and
followed by exactly one space.

## Variants

### `↯/c.6.1.1` — No space before a comma

A space appeared between the previous token and the comma.

**Example.** `[↯/f.21.1/c.6.1.1] no space is permitted before a comma`

**Resolution.** Remove the space before the `,`.

### `↯/c.6.1.2` — Exactly one space after a comma

The character following the comma was not a single space (either
zero, more than one, or a non-space character without a permitted
alignment exception).

**Example.** `[↯/f.21.1/c.6.1.2] exactly one space is required after a comma`

**Resolution.** Use exactly one space after each `,`.

## See also

- [The standard](../consequent-style.md)
- Source: `src/plugin/flare.ProximityRules.scala`
