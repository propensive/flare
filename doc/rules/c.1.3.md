# `↯/c.1.3` — Blank line after `package`

**Principle:** `↯/c.1` — The Frame

Raised by flare when line `header + 2` (immediately
after the `package` declaration) is not blank.

## Cause

A single blank line must separate the `package` declaration from
whatever follows.

## Example message

```
[↯/f.21.1/c.1.3] line `header + 2` must be a single blank line after `package`
```

## Resolution

Insert a single blank line on line `header + 2`.

## See also

- [The standard](../consequent-style.md)
- Source: `src/plugin/flare.FrameRules.scala`
