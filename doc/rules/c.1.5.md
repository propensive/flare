# `↯/c.1.5` — Tab characters

**Principle:** `↯/c.1` — The Frame

Raised by flare when a tab character appears
anywhere in source.

## Cause

Consequent Style requires that all indentation and intra-line spacing
use spaces, not tabs.

## Example message

```
[↯/f.21.1/c.1.5] tab character is not permitted; use spaces
```

## Resolution

Configure the editor to insert spaces; replace any existing tabs
with the appropriate number of spaces.

## See also

- [The standard](../consequent-style.md)
- Source: `src/plugin/flare.FrameRules.scala`
