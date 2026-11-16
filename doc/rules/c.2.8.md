# `↯/c.2.8` — Multi-line interpolated-string layout

**Principle:** `↯/c.2` — Anchoring

Raised by flare when the house-style rule on multi-line interpolated-string layout is broken.

## Cause

The content of a multi-line interpolated string begins on the line after the opening quotes,
and its lines are indented consistently with the opening.

## Variants

| Code | Message |
| --- | --- |
| `↯/c.2.8.1` | the content of a multi-line `<info><q>` string must begin on the line after the opening quotes |
| `↯/c.2.8.2` | the content of a multi-line `<info><q>` string must be indented to column <expected> |
| `↯/c.2.8.3` | a line of a multi-line `<info><q>` string must not be indented less than the first content line (column <expected>) |
| `↯/c.2.8.4` | the closing `<q>` of a multi-line `<info><q>` string must be alone on its line, aligned with the opening quotes (column <col>) |

## Resolution

Move the content onto its own line and indent it as the message directs, which keeps the
string's content visually distinct from the code around it.

This is a style rule rather than a correctness one. The house style is set out in
`doc/consequent-style.md`.

## See also

- [The standard](../consequent-style.md)
- Source: `src/plugin/flare.AnchorRules.scala`
