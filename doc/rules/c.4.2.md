# `↯/c.4.2` — Symbolic infix operator continuation

**Principle:** `↯/c.4` — Continuation Marking

Raised by flare when the house-style rule on symbolic infix operator continuation is broken.

## Cause

An infix operator continuing an expression onto another line terminates the first line
rather than beginning the second, is preceded by exactly one space, and the continuation
is indented two columns past the anchor. The one-space requirement is Balance (↯/c.6.5)
applied at the break: a line break on one side of an infix token demands exactly one
space on the other, so `left ++⏎` — never `left++⏎` — even for operators legally
written tight (`a++b`) when they fit on one line.

## Variants

| Code | Message |
| --- | --- |
| `↯/c.4.2.1` | a multi-line infix operator must terminate the first line, not begin the continuation line |
| `↯/c.4.2.2` | a multi-line infix continuation must be indented <op> columns (found <op>) |
| `↯/c.4.2.3` | a trailing infix operator must be preceded by exactly one space, balancing the line break that follows it |

## Resolution

Move the operator to the end of the first line, with a single space before it, and
indent the continuation as directed. Reading down the left margin then shows the
structure of the expression.

This is a style rule rather than a correctness one. The house style is set out in
`doc/consequent-style.md`.

## See also

- [The standard](../consequent-style.md)
- Source: `src/plugin/flare.ContinuationRules.scala`
