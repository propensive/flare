# `↯/c.1.8` — Block-comment usage

**Principle:** `↯/c.1` — The Frame

Raised by flare when a `/* … */` block comment
appears outside its permitted use.

## Cause

Consequent Style reserves block comments for the licence header, which
occupies the first `header` lines of the file. All other comments must be line comments (`//`).
Documentation should live in `doc/` markdown rather than `/** … */`
docstrings.

## Variants

### `↯/c.1.8.1` — No `/** … */` doc comments

A scaladoc-style block comment was found.

**Example.** `[↯/f.21.1/c.1.8.1] `/** ... */` block comments are not permitted; use `doc/` markdown instead`

**Resolution.** Convert the docstring into prose under `doc/`, or
into a line comment if it explains a non-obvious mechanism inline.

### `↯/c.1.8.2` — No `/* … */` block comments outside the licence

A plain block comment appeared outside lines 1–32.

**Example.** `[↯/f.21.1/c.1.8.2] `/* ... */` block comments are reserved for the license header (lines 1-32)`

**Resolution.** Replace with one or more line comments.

## See also

- [The standard](../consequent-style.md)
- Source: `src/plugin/flare.FrameRules.scala`
