# `↯/c.2.3` — Type-annotation anchor

**Principle:** `↯/c.2` — Anchoring

Raised by flare when a definition's type-annotation `:` sits in neither permitted position.

## Cause

A definition's type annotation either follows its name on the same line, or
begins a line in the definition's anchor column. Those are the only two
positions, so a reader scanning the left margin can always tell a continued
signature from a new definition.

## Example message

```
[↯/f.21.1/c.2.3] type-annotation `:` should align with the definition's anchor at column 3 (found 5)
```

## Resolution

Move the `:` onto the same line as the name, or into the anchor column.

## See also

- [The standard](../consequent-style.md)
- Source: `src/plugin/flare.AnchorRules.scala`
