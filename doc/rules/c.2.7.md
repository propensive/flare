# `↯/c.2.7` — `given` continuation alignment

**Principle:** `↯/c.2` — Anchoring

Raised by flare when the house-style rule on `given` continuation alignment is broken.

## Cause

A `=>` continuing a `given` signature onto the next line must align at the column the
house style prescribes, so that the parts of a multi-line contextual definition line up.

## Example message

```
[↯/f.21.1/c.2.7] `=>` continuation of a `given` signature should align at column <s> (found <leadingCols>)
```

## Resolution

Align the continuation as the message directs. The column follows from the definition's
anchor rather than being chosen freely.

This is a style rule rather than a correctness one. The house style is set out in
`doc/consequent-style.md`.

## See also

- [The standard](../consequent-style.md)
- Source: `src/plugin/flare.AnchorRules.scala`
