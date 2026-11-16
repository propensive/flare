# `↯/c.8.4` — Definition not exported to the umbrella package

**Principle:** `↯/c.8` — Locatability

Raised by flare when the house-style rule on definition not exported to the umbrella package is broken.

## Cause

Every public module is re-exported into the project's umbrella package, so that a
single import reaches it. This rule reports modules that are not.

It fires only when the `umbrella` option is set; a project without an umbrella
package has nothing to check.

## Example message

```
[↯/f.21.1/c.8.4] the module is defined but not exported to the `umbrella` package: `Parser`
```

## Resolution

Add the definition to the module's export surface. If it is deliberately
internal, make it private rather than leaving it public and unexported.

This is a style rule rather than a correctness one. The house style is set out in
`doc/consequent-style.md`.

## See also

- [The standard](../consequent-style.md)
- Source: `src/plugin/flare.LocatabilityRules.scala`
