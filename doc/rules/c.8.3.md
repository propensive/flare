# `↯/c.8.3` — `object` must precede `class`/`trait`/`enum` of same name

**Principle:** `↯/c.8` — Locatability

Raised by flare when a companion `object`
appears below the type it accompanies.

## Cause

Consequent Style places the companion `object` before the
`class`/`trait`/`enum` it pairs with.

## Example message

```
[↯/f.21.1/c.8.3] object `Foo` must appear before class/trait/enum `Foo`
```

## Resolution

Move the `object` declaration above the type declaration.

## See also

- [The standard](../consequent-style.md)
- Source: `src/plugin/flare.LocatabilityRules.scala`
