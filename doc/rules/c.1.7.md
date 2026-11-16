# `↯/c.1.7` — Trailing whitespace

**Principle:** `↯/c.1` — The Frame

Raised by flare when a line has whitespace
after its last visible character.

## Cause

Consequent Style forbids trailing spaces or tabs on any line.

## Example message

```
[↯/f.21.1/c.1.7] line has trailing whitespace
```

## Resolution

Strip the trailing whitespace; many editors can do this on save.

## See also

- [The standard](../consequent-style.md)
- Source: `src/plugin/flare.FrameRules.scala`
