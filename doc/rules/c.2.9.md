# `↯/c.2.9` — Quote and splice layout

**Principle:** `↯/c.2` — Anchoring

Raised by flare when the layout of a quoted or spliced block is broken.

## Cause

The layout of a quoted or spliced block is prescribed in detail: where the
opener sits, how far the body is indented, where the closing brace goes, and
what spacing surrounds an inline quote. Staged code then reads consistently
with the code around it.

The rules of this family are contractually frozen; their normative text and
examples are in the standard.

## Variants

| Code | Message |
| --- | --- |
| `↯/c.2.9.1` | closing `}` of a multi-line quote/splice at column <cols> does not align with its opening `{` at column <opener> |
| `↯/c.2.9.2` | `<arr>` and `{` of a multi-line quote/splice must be separated by exactly one space |
| `↯/c.2.9.3` | the `<arr> {` opener of a multi-line quote/splice must be alone on its line (only enclosing `(` / `{` permitted) |
| `↯/c.2.9.4` | body of a multi-line quote/splice must be indented to column <top> (found <leadingCols>) |
| `↯/c.2.9.5` | closing `}` of a multi-line quote/splice must be alone on its line (only trailing `)` / `}` permitted) |
| `↯/c.2.9.6` | no space is permitted directly after `<prefix><text>` in an inline quote/splice |

## Resolution

Follow the layout the message names; the sub-code identifies which aspect is at
fault.

## See also

- [The standard](../consequent-style.md)
- Source: `src/plugin/flare.QuoteRules.scala`
