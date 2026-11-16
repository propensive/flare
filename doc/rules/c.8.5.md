# `↯/c.8.5` — Extension method not exported to the umbrella package

**Principle:** `↯/c.8` — Locatability

Raised by flare when a public top-level extension method is missing from the umbrella package.

## Cause

An extension method is only found by the compiler if it is in scope, so a
project with an umbrella re-export package must re-export its public top-level
extension methods by name, exactly as it re-exports its modules (`↯/c.8.4`).

This rule fires only when the `umbrella` option is set; a project without an
umbrella package has nothing to check.

## Example message

```
[↯/f.21.1/c.8.5] the extension method is defined but not exported to the `umbrella` package: `describe`
```

## Resolution

Add the method's leaf name to the module's export surface, or mark the
definition `@unexported` if it is deliberately not part of the public surface.

## See also

- [The standard](../consequent-style.md)
- Source: `src/plugin/flare.LocatabilityRules.scala`
