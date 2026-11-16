# `↯/c.7.2` — `using`-clause alignment

**Principle:** `↯/c.7` — Tabulation

Raised by flare when a `using`-clause
parameter is not aligned at the column the rule expects.

## Cause

Continuation lines of a `using` clause must align at a specific
column derived from the surrounding signature.

## Example message

```
[↯/f.21.1/c.7.2] using-clause parameter should align at column 14 (found 12)
```

## Resolution

Adjust the leading whitespace so the parameter starts at the
indicated column.

## See also

- [The standard](../consequent-style.md)
- Source: `src/plugin/flare.TabulationRules.scala`
