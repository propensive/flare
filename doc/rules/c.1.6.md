# `↯/c.1.6` — Indent width must be even

**Principle:** `↯/c.1` — The Frame

Raised by flare when a non-blank line's
leading spaces are not a multiple of 2.

## Cause

Consequent Style indents in steps of two columns.

## Example message

```
[↯/f.21.1/c.1.6] indent width 3 is not a multiple of 2
```

## Resolution

Adjust the indentation to the nearest multiple of 2.

## See also

- [The standard](../consequent-style.md)
- Source: `src/plugin/flare.FrameRules.scala`
