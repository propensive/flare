# `↯/c.2.5` — Indented scope body

**Principle:** `↯/c.2` — Anchoring

Raised by flare when the body of an indented scope is not at anchor + 2.

## Cause

The body of an indented scope — a colon-block, a `match`, a lambda or `case`
arrow, or a definition's `=` right-hand side — sits at exactly two columns past
its anchor. Not one, not four: the depth of a construct is then readable
directly from its indentation.

Bracket and quote interiors are governed by `↯/c.5.4`, `↯/c.5.5` and `↯/c.2.9` instead.

## Example message

```
[↯/f.21.1/c.2.5] indented scope body must be indented to column 5 (anchor + 2); found column 7
```

## Resolution

Indent the body to exactly two columns past the anchor of the construct that
opens it.

## See also

- [The standard](../consequent-style.md)
- Source: `src/plugin/flare.AnchorRules.scala`
