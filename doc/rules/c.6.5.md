# `↯/c.6.5` — Blank line after imports

**Principle:** `↯/c.6` — Proximity

Raised by flare when there is no blank line
between the last import and the first declaration in the file.

## Cause

A single blank line must separate the import section from the
body.

## Example message

```
[↯/f.21.1/c.6.5] missing blank line between imports and first declaration
```

## Resolution

Add a blank line after the last `import`.

## See also

- [The standard](../consequent-style.md)
- Source: `src/plugin/flare.FrameRules.scala`
