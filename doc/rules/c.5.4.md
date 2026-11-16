# `↯/c.5.4` — Multi-line block bracket spacing

**Principle:** `↯/c.5` — Balance

Raised by flare when bracket spacing in a
multi-line block does not follow the documented form.

## Cause

In a multi-line block, the opening bracket must be followed by a
space, the closing bracket must be preceded by a space, and the
closing bracket must not appear alone on its own line.

## Example messages

```
[↯/f.21.1/c.5.4] a space is required after `(` in a multi-line block
```

```
[↯/f.21.1/c.5.4] a space is required before `)` in a multi-line block
```

```
[↯/f.21.1/c.5.4] `)` cannot appear alone on its line; the closing bracket of a multi-line block goes on the same line as the last parameter
```

## Resolution

Adjust the bracket spacing to match the rule, or convert the block
to single-line form (which uses different spacing — see `↯/c.5.5`).

## See also

- [The standard](../consequent-style.md)
- Source: `src/plugin/flare.BalanceRules.scala`
