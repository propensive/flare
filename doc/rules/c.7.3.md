# `↯/c.7.3` — `for`-comprehension layout

**Principle:** `↯/c.7` — Tabulation

Raised by flare when the house-style rule on `for`-comprehension layout is broken.

## Cause

Generators in a `for` comprehension align with the first generator's left-hand side, and
blank lines separate enumerators where the rule requires them.

## Variants

| Code | Message |
| --- | --- |
| `↯/c.7.3.1` | `<-`/`=` should be vertically aligned at column <anchorOpCol> (found <gl>) |
| `↯/c.7.3.2` | generator should align with the first generator's LHS at column <anchorLhsCol> (found <gl>) |
| `↯/c.7.3.3` | `if` filter should align with `<-`/`=` at column <anchorOpCol> (found <gl>) |
| `↯/c.7.3.4` | `if` filter must not be separated from the preceding enumerator by a blank line |

## Resolution

Align the generators and place the blank lines as the message directs.

This is a style rule rather than a correctness one. The house style is set out in
`doc/consequent-style.md`.

## See also

- [The standard](../consequent-style.md)
- Source: `src/plugin/flare.TabulationRules.scala`
