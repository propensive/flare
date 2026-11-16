# `↯/c.3.2` — Lambda bracketing

**Principle:** `↯/c.3` — Density

Raised by flare when the house-style rule on lambda bracketing is broken.

## Cause

How a lambda is bracketed depends on its shape: a multi-line lambda uses the colon-argument
form, an underscore lambda is wrapped in parentheses, and a named-parameter lambda in braces.

## Variants

| Code | Message |
| --- | --- |
| `↯/c.3.2.1` | named-parameter lambda must be wrapped in `{…}`, not `(…)` (or use `f: x => …` since the lambda is last on the line) |
| `↯/c.3.2.2` | lambda is the last thing on its line; prefer `f: x => …` over `f { x => … }` |
| `↯/c.3.2.3` | multi-line lambda must use a colon-arg `f: x => …` form, not `<s>` |
| `↯/c.3.2.4` | anonymous (`_`-)lambda must be wrapped in `(…)`, not `<s>` |

## Resolution

Use the form the message names. The rule exists so that a reader can tell a lambda's shape
from its brackets alone.

This is a style rule rather than a correctness one. The house style is set out in
`doc/consequent-style.md`.

## See also

- [The standard](../consequent-style.md)
- Source: `src/plugin/flare.DensityRules.scala`
