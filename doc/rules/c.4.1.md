# `↯/c.4.1` — `.method` continuation lines

**Principle:** `↯/c.4` — Continuation Marking

Raised by flare when a `.method` continuation
on its own line is not separated from the previous code line in the
required way.

## Cause

When a `.method` chain continues on a new line, Consequent enforces a
blank-line rule that depends on the previous code line's indentation.

## Variants

### `↯/c.4.1.1` — Blank line required before continuation

A `.method` continuation followed a more-indented line without a
blank line between them.

**Example.** `[↯/f.21.1/c.4.1.1] blank line is required before `. method` continuation following a more-indented line`

**Resolution.** Add a blank line before the `.method` line.

### `↯/c.4.1.2` — No blank line at the same indent

A `.method` continuation at the same indent as the previous code
line was preceded by an unwanted blank line.

**Example.** `[↯/f.21.1/c.4.1.2] no blank line is permitted before `. method` continuation at the same indent`

**Resolution.** Remove the blank line.

## See also

- [The standard](../consequent-style.md)
- Source: `src/plugin/flare.ContinuationRules.scala`
