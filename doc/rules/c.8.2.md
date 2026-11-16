# `↯/c.8.2` — File naming conventions

**Principle:** `↯/c.8` — Locatability

Raised by flare when a file's name does not
match a documented naming convention.

## Cause

Consequent Style uses a small set of file-name conventions: types live in
files named after the type, package-level givens live in
`<package>_<scope>.scala` files, and so on. Names outside this set
are rejected.

## Example message

```
[↯/f.21.1/c.8.2] file name `helpers.scala` does not match a documented naming convention
```

## Resolution

Rename the file to match the convention for its content kind. See
the Consequent documentation for the full list of permitted patterns.

## See also

- [The standard](../consequent-style.md)
- Source: `src/plugin/flare.LocatabilityRules.scala`
