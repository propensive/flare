# `↯/c.5.1` — Operator spacing

**Principle:** `↯/c.5` — Balance

Raised by flare when whitespace around a
binary operator is inconsistent or violates the spacing-by-precedence
rules.

## Cause

Single-character operators may have either zero or one space on
each side, but the spacing must be symmetric. Multi-character
operators must have one space on each side. Within an expression,
operators of the same precedence must use the same spacing, and
higher-precedence operators must have at most as much spacing as
lower-precedence ones.

## Example messages

```
[↯/f.21.1/c.5.1] `+` has asymmetric spacing — use 0 or 1 space on both sides
```

```
[↯/f.21.1/c.5.1] multi-character `&&` requires one space on each side
```

```
[↯/f.21.1/c.5.1] `*` has inconsistent spacing with same-precedence operators
```

```
[↯/f.21.1/c.5.1] `*` cannot have more spacing than lower-precedence operators in the same expression
```

## Resolution

Normalise the spacing in the expression so all operator pairs
follow the documented precedence-spacing relationship.

## See also

- [The standard](../consequent-style.md)
- Source: `src/plugin/flare.BalanceRules.scala`
