# `↯/c.6.2` — Blank-line spacing between sibling declarations

**Principle:** `↯/c.6` — Proximity

Raised by flare when the number of blank
lines between two sibling declarations does not match the rule.

## Cause

Sibling declarations at the same indent require a specific number
of blank lines between them, depending on declaration kind.

## Example message

```
[↯/f.21.1/c.6.2] expected 1 blank line(s) between sibling declarations (saw 0)
```

## Resolution

Insert or remove blank lines so the count matches the expected
value reported in the message.

## See also

- [The standard](../consequent-style.md)
- Source: `src/plugin/flare.ProximityRules.scala`
