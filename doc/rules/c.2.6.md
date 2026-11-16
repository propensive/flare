# `↯/c.2.6` — Signature `=` placement

**Principle:** `↯/c.2` — Anchoring

Raised by flare when the `=` introducing a body is not the last token of a multi-line signature.

## Cause

When a definition's signature spans several lines, the `=` that introduces the
body ends the final line of the signature. That makes the boundary between
signature and body a single, fixed visual marker rather than something the
reader has to find.

## Example message

```
[↯/f.21.1/c.2.6] the body-introducing `=` of a multi-line definition signature must be the last token on the signature's final line
```

## Resolution

Move the `=` to the end of the signature's last line and start the body on the
line below.

## See also

- [The standard](../consequent-style.md)
- Source: `src/plugin/flare.AnchorRules.scala`
