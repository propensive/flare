# `↯/c.1.2` — `package` declaration on line `header + 1`

**Principle:** `↯/c.1` — The Frame

Raised by flare when line `header + 1` is not
`package <module>` matching the source's module.

## Cause

After the 32-line licence header, line `header + 1` must declare the package.
The package name must be a single identifier segment matching the
file's expected module.

## Example messages

```
[↯/f.21.1/c.1.2] expected `package` declaration on line `header + 1`, found content on line 34
```

```
[↯/f.21.1/c.1.2] package declaration must be a single identifier segment, not `foo.bar`
```

```
[↯/f.21.1/c.1.2] package `foo` does not match expected module `bar`
```

```
[↯/f.21.1/c.1.2] package declaration must contain only `package <ident>` on line `header + 1`
```

```
[↯/f.21.1/c.1.2] line `header + 1` must be `package <module>`
```

## Resolution

Place a single `package <module>` declaration on line `header + 1`, where
`<module>` is the module the file belongs to.

## See also

- [The standard](../consequent-style.md)
- Source: `src/plugin/flare.FrameRules.scala`
