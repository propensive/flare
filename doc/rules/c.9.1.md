# `↯/c.9.1` — Unsafe naming

**Principle:** `↯/c.9` — Soundness

Raised by flare when a method is gated by one of the project's gates but not
named for it, or named for it but not gated.

The rule fires only for the gates the project declares — under its `style
consequent`, a `gate <Token>` with an optional `prefix <name>` child (the
default prefix is the token's simple name in lower case):

```
gate Unsafe
  prefix unsafe
```

A project that declares no gate has nothing to check. A project may declare
several, each with its own prefix; a name is checked against the gate whose
prefix it claims.

## Cause

A project that concentrates its unsafety behind a token — a value that must
be supplied to call any operation bypassing a guarantee the compiler would
otherwise enforce — depends on the token being a reliable index of where the
guarantee stops. Two spellings break that index, and each is a different
lie:

- `↯/c.9.1.1`, a **gated method with an ordinary name**. The gate is real, but a
  reader scanning for the boundary does not see one. `raw`, `datum` and
  `child` read as ordinary accessors; only their signatures reveal that
  they are not.
- `↯/c.9.1.2`, an **`unsafe`-prefixed method with no gate**. The name claims a
  boundary that nothing enforces, so the prefix stops meaning anything, and
  callers pay attention to it at exactly the sites where attention buys
  nothing.

The prefix must be a word of its own — `unsafe` alone, or `unsafe` followed by
an uppercase letter — so `unsafely`, the block that supplies the token, is
exempt by construction, as is any other name that merely continues in lower
case. (The examples use the `Unsafe`/`unsafe` gate; every other gate reads the
same way with its own token and prefix.)

Two definitions are outside the rule because neither can carry the prefix: a
**constructor**, which takes its class's name, and a **`given`**, which is
summoned by type rather than called by name. Both remain gated; the
soundness argument for them belongs in a comment.

A `using` parameter of the token's type is what counts as a gate. The
token is matched on its simple name, so `Unsafe` and `vacuous.Unsafe` are
the same token, and an `erased` or anonymous parameter gates as well as a
named one. A token passed as an *ordinary* parameter is not a gate: it is
then a value the method happens to take, not a capability its callers must
hold.

## Example message

```
[↯/f.21.1/c.9.1.1] `raw` is gated by `Unsafe`, so its name must begin `unsafe`
[↯/f.21.1/c.9.1.2] `unsafeRead` is named as `unsafe`, so it must take `(using erased Unsafe)`
```

## Resolution

For `↯/c.9.1.1`, rename the method with an `unsafe` prefix: `raw` becomes
`unsafeRaw`. Where the name cannot take one — an `apply`, most often —
give the method a real name (`unsafeFrom`) and let the factory go.

For `↯/c.9.1.2`, add the gate: `(using erased Unsafe)`. If the operation turns
out not to need one, the honest fix is the other way round — drop the
prefix, because the name was overstating what the method does.

This is a house-style rule, but unlike the other principles it is about
what the code claims rather than how it reads. The standard is set out in
`doc/consequent-style.md`.

## See also

- [The standard](../consequent-style.md)
- Source: `src/plugin/flare.SoundnessRules.scala`
