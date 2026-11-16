# `↯/c.5.2` — Assignment spacing

**Principle:** `↯/c.5` — Balance

Raised by flare when an assignment operator has no space before it.

## Cause

An assignment operator needs at least one space before it when its right-hand
side is on the same line. Assignment is the most significant boundary in a
statement, so it is never allowed to run into the name on its left.

## Example message

```
[↯/f.21.1/c.5.2] `=` requires at least one space before it when the right-hand side is on the same line
```

## Resolution

Put a space before the operator.

## See also

- [The standard](../consequent-style.md)
- Source: `src/plugin/flare.BalanceRules.scala`
