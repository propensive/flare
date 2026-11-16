# `↯/c.6.3` — Too many consecutive blank lines

**Principle:** `↯/c.6` — Proximity

Raised by flare when a run of blank lines is longer
than the style permits.

## Cause

Consequent Style allows at most **one** blank line anywhere, with a single
exception: a run of up to **two** blank lines may sit immediately before,
or immediately after, a definition with a heavy signature (one whose
signature includes a `:   ReturnType =` line). The two-blank gap is the
top of the Proximity gap scale (none < space < newline < blank < two
blanks), and it marks the heaviest construct the style recognises.

## Example messages

```
[↯/f.21.1/c.6.3] more than one consecutive blank line (two are permitted only around a heavy-signature definition)
[↯/f.21.1/c.6.3] more than two consecutive blank lines
```

## Resolution

Remove the surplus blank lines.

## See also

- [The standard](../consequent-style.md)
- Source: `src/plugin/flare.ProximityRules.scala`
