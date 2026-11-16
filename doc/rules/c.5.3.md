# `↯/c.5.3` — Single space between specific tokens

**Principle:** `↯/c.5` — Balance

Raised by flare when exactly one space is
required between two adjacent tokens but a different amount appears.

## Cause

Certain token pairs (the rule recogniser identifies them by token
kind) require exactly one space between them.

## Example message

```
[↯/f.21.1/c.5.3] a single space is required between `if` and `(`
```

## Resolution

Place exactly one space between the two tokens identified.

## See also

- [The standard](../consequent-style.md)
- Source: `src/plugin/flare.BalanceRules.scala`
