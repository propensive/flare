# `↯/c.1.4` — Line length

**Principle:** `↯/c.1` — The Frame

Raised by flare when a line exceeds the
configured column limit.

## Cause

Consequent Style limits each line to `columns` visible columns — 100 by
default — where zero-width and combining characters do not count.

## Example message

```
[↯/f.21.1/c.1.4] line exceeds 100 columns (is 117 columns)
```

## Resolution

Wrap the line; long expressions can usually be broken at function
calls, operators, or after `=`.

## See also

- [The standard](../consequent-style.md)
- Source: `src/plugin/flare.FrameRules.scala`
