# `↯/c.6.6` — Annotations and following declarations

**Principle:** `↯/c.6` — Proximity

Raised by flare when an annotation is not
attached to the immediately following declaration.

## Cause

An annotation (`@Foo`) must sit directly above the declaration it
annotates, with no blank line between them, and must be followed by
a declaration.

## Variants

### `↯/c.6.6.1` — Annotation not followed by a declaration

An annotation was the last meaningful token before the file ended
or a non-declaration appeared next.

**Example.** `[↯/f.21.1/c.6.6.1] annotation is not followed by a declaration`

**Resolution.** Place the declaration the annotation refers to
immediately after the annotation, or remove the orphan annotation.

### `↯/c.6.6.2` — No blank line between annotation and declaration

A blank line separated the annotation from its declaration.

**Example.** `[↯/f.21.1/c.6.6.2] blank line is not permitted between an annotation and the declaration it annotates`

**Resolution.** Remove the blank line.

## See also

- [The standard](../consequent-style.md)
- Source: `src/plugin/flare.ProximityRules.scala`
