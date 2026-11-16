# `↯/c.6.4` — Blank line after heavy-signature return type

**Principle:** `↯/c.6` — Proximity

Raised by flare when a heavy-signature
declaration's return-type line is not followed by a blank line
before the body.

## Cause

When a method's signature spans multiple lines and its return type
appears on its own line, the body must be separated from it by a
blank line.

## Example message

```
[↯/f.21.1/c.6.4] a blank line is required between a heavy-signature return type and the body
```

## Resolution

Insert a blank line after the return-type line.

## See also

- [The standard](../consequent-style.md)
- Source: `src/plugin/flare.ProximityRules.scala`
