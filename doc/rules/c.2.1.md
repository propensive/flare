# `↯/c.2.1` — Continuation indent

**Principle:** `↯/c.2` — Anchoring

Raised by flare when a line is indented more than two columns deeper than the line above it.

## Cause

Indentation steps by two columns at a time. An indent that jumps further is
almost always an *alignment* indent — lining a continuation up under a name on
the previous line — which breaks down as soon as the name is renamed. When a
continuation needs to sit deeper, split the previous line instead, so the depth
is reached by ordinary stepping.

Two exceptions allow a step of four: a line following a quote or splice opener,
and a line following a chain method-application opener. The rule is suspended
inside an unclosed bracket, inside a multi-line import's selector list, on a
lone `for`-filter row, and on the first body line of an indented scope, each of
which is governed by a rule of its own.

## Example message

```
[↯/f.21.1/c.2.1] indent 8 cannot exceed previous line's indent 2 by more than 2
```

## Resolution

Break the previous line so the deeper indent is reached in steps of two, rather
than aligning under something on the line above.

## See also

- [The standard](../consequent-style.md)
- Source: `src/plugin/flare.AnchorRules.scala`
