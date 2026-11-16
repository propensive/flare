# `↯/c.7.1` — `=>` alignment in case run

**Principle:** `↯/c.7` — Tabulation

Raised by flare when a `case` clause's `=>`
is not aligned with the run of cases it belongs to.

## Cause

Within a contiguous run of single-line `case` clauses — consecutive cases
at the same indent with no intervening blank line — every `=>` must be in
the same column, determined by the longest pattern in the run. A
multi-line case, or a blank line, ends the run; cases after it start a
fresh run with its own column.

The rule holds everywhere: there is no exemption for existing code.

## Example message

```
[↯/f.21.1/c.7.1] `=>` should be at column 17 to align with the case run
```

## Variants

A case whose pattern or body spans lines is not part of a run, and has its own failures:

| Code | Message |
| --- | --- |
| `↯/c.7.1.1` | `=>` of a multi-line case pattern must trail the last pattern token on the same line, not stand alone on its own line |
| `↯/c.7.1.2` | body of a multi-line case pattern must start on the line after `=>` |
| `↯/c.7.1.3` | exactly one space is required before `=>` in a multi-line case |

## Resolution

Adjust the spacing before each `=>` so all of them sit in the same
column within the run.

## See also

- [The standard](../consequent-style.md)
- Source: `src/plugin/flare.TabulationRules.scala`
