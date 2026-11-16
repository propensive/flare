# `↯/c.3.1` — Unnecessary line break

**Principle:** `↯/c.3` — Density

Raised by flare when a construct is spread over
several source lines although its one-line rendering would fit within
100 columns.

## Cause

Under the Density principle, the most compact compliant layout that fits
is the only correct one: a line break must always mean "didn't fit". A
break that isn't forced by the line-length limit invites the reader to
look for a reason that isn't there, and makes two equal-sized constructs
look different.

In its first version the rule deliberately checks only three
high-confidence families of construct:

- keyword sequences (`if`/`then`/`else`, `while`/`do`, `for`/`yield`,
  `for`/`do`, `try`/`finally`);
- symbolic infix operator chains, measured from the top of each chain
  (an `else if` link or a chain operand is never flagged on its own,
  since one-lining it alone would break the enclosing cascade,
  `↯/c.2.2.2`);
- lambdas whose body starts on a later line than their parameters.

A site is *bailed* — never flagged — whenever its one-line rendering
cannot be proved to be a simple whitespace-collapsing join: comments,
multi-line strings, colon-block interiors, `case` keywords and `match`
expressions, `end`-marker lines, blank interior lines, multi-statement
block bodies, `for`s whose enumerator section itself spans lines, and
any scanner ambiguity all disqualify the site. The measured width joins
every break with exactly one space even where a tighter spelling would
be legal, so the conservatism can only suppress a finding, never invent
one. The scope is expected to widen in later versions.

## Example message

```
[↯/f.21.1/c.3.1] this if-expression fits on one line (would be 74 columns); breaks are reserved for code that does not fit
```

## Resolution

Join the construct onto one line. The message reports the width the
one-line form would have, which is always within the 100-column limit
when this error is raised.

This is a style rule rather than a correctness one. The house style is
set out in `doc/consequent-style.md`.

## See also

- [The standard](../consequent-style.md)
- Source: `src/plugin/flare.DensityRules.scala`
