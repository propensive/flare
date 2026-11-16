# `↯/c.2.4` — Anchor of a heavy argument block

**Principle:** `↯/c.2` — Anchoring

Raised by flare when a line opening with `(` or `[` follows a line whose anchor is mid-line.

## Cause

A line whose first token is `(` or `[` is a *heavy* continuation: its anchor is
the anchor of the line above. That only reads unambiguously when the preceding
line is a tight expression. If the preceding line contains a top-level operator
or an assignment, its anchor is somewhere in the middle of the line, and the
continuation has nothing stable to hang from.

## Example message

```
[↯/f.21.1/c.2.4] heavy `(`/`[` continuation must follow a tight expression on its own line; the preceding line contains a top-level operator or assignment, so the anchor is mid-line
```

## Resolution

Split the preceding line so that the token the bracket continues from ends it,
or bring the bracket up onto that line.

## See also

- [The standard](../consequent-style.md)
- Source: `src/plugin/flare.AnchorRules.scala`
