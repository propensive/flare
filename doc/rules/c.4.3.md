# `↯/c.4.3` — Continuation indents after `=>` and heavy `:`

**Principle:** `↯/c.4` — Continuation Marking

Raised by flare when a continuation line
following a `=>` or heavy-signature `:` does not have the required
fixed indent.

## Cause

A `=>` continuation line must be followed by exactly two spaces; a
heavy-signature return-type `:` continuation must be followed by
exactly three spaces.

## Example messages

```
[↯/f.21.1/c.4.3] `=>` continuation line must be followed by exactly two spaces
```

```
[↯/f.21.1/c.4.3] heavy-signature return type `:` must be followed by exactly three spaces
```

## Resolution

Adjust the leading whitespace on the continuation line to match.

## See also

- [The standard](../consequent-style.md)
- Source: `src/plugin/flare.ContinuationRules.scala`
