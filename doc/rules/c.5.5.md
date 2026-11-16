# `↯/c.5.5` — Bracket inner spacing

**Principle:** `↯/c.5` — Balance

Raised by flare when there is unwanted space
immediately inside a single-line bracket pair.

## Cause

In single-line constructs, no space is permitted directly after an
opening bracket or directly before a closing bracket.

## Example messages

```
[↯/f.21.1/c.5.5] no space is permitted directly after `(`
```

```
[↯/f.21.1/c.5.5] no space is permitted directly before `)`
```

## Resolution

Remove the space adjacent to the bracket; multi-line forms use a
different rule (see `↯/c.5.4`).

## See also

- [The standard](../consequent-style.md)
- Source: `src/plugin/flare.BalanceRules.scala`
