# `↯/c.2.2` — Keyword-sequence layout

**Principle:** `↯/c.2` — Anchoring

Raised by flare when the house-style rule on anchor alignment is broken.

## Cause

A keyword sequence — `if`/`then`/`else`, `for`/`yield`, `for`/`do`, `while`/`do`,
`try`/`catch`/`finally` — has a single break point. Every keyword before it sits on the
anchor's line; every keyword from it onward starts a line in the anchor's column. Bodies
cascade the same way: once one body is indented onto its own line, every later one is too.

## Variants

| Code | Message |
| --- | --- |
| `↯/c.2.2.1` | keyword `<elem>` must start on the same line as `<anchor>` (line <anchorLine>) or on a new line in column <anchorCol> (found line <elem>, column <elem>) |
| `↯/c.2.2.2` | body after `<elem>` must be indented onto a new line because an earlier body in this sequence is |

## Resolution

Break the sequence at a single point, and indent every body from the first indented one onward.

This is a style rule rather than a correctness one. The house style is set out in
`doc/consequent-style.md`.

## See also

- [The standard](../consequent-style.md)
- Source: `src/plugin/flare.AnchorRules.scala`
