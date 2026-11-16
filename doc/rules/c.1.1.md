# `↯/c.1.1` — Licence-header block comment

**Principle:** `↯/c.1` — The Frame

Raised by flare when a file does not begin with the project's licence header.

## Cause

Every source file opens with the project's licence header — the text of the
file named by `header` in the project's `style consequent` stanza — verbatim,
line for line. The header file is the single source of truth: a header that
has drifted from it, by a version, a year or a stray space, is reported at the
first line that differs.

The header file may be a template: a `${NAME}` placeholder — the `envsubst`
convention, which is how Soundness applies its own — stands for any text, and
the padding that follows it may differ in length.

A project that declares no `header` has no licence header, and this rule (with
[`↯/c.1.2`](c.1.2.md) and [`↯/c.1.3`](c.1.3.md), which place the `package` declaration after
it) does not fire.

## Example message

```
[↯/f.21.1/c.1.1] line 14 must be the licence header's, `Flare, version 0.1.0.`
```

```
[↯/f.21.1/c.1.1] the file ends before the licence header does
```

## Resolution

Copy the project's licence header verbatim from its header file; pyrocosm's
`etc/headers.sh` shows how a project re-applies it to every source.

## See also

- [The standard](../consequent-style.md)
- Source: `src/plugin/flare.FrameRules.scala`
