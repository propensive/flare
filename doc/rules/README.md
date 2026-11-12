# Rule reference

Every rule has an identifier of the form `<principle><number>` — the letter of
the principle it derives from, then its number within that principle. A rule
with several distinct failures adds a third component: `A2.1`, `A9.3`.

Numbering within a principle follows the order of Part II of
[the standard](../consequent-style.md). The number carries no meaning beyond
identity, and is stable: a rule keeps its identifier even if the section order
changes.

## F — The Frame

*The fixed shape every file sits in.*

| Rule | |
| --- | --- |
| [`F1`](F1.md) | Licence-header block comment |
| [`F2`](F2.md) | `package` declaration on line `header + 1` |
| [`F3`](F3.md) | Blank line after `package` |
| [`F4`](F4.md) | Line length |
| [`F5`](F5.md) | Tab characters |
| [`F6`](F6.md) | Indent width must be even |
| [`F7`](F7.md) | Trailing whitespace |
| [`F8`](F8.md) | Block-comment usage |

## A — Anchoring

*Where a continuation line begins.*

| Rule | |
| --- | --- |
| [`A1`](A1.md) | Continuation indent |
| [`A2`](A2.md) | Keyword-sequence layout |
| [`A3`](A3.md) | Type-annotation anchor |
| [`A4`](A4.md) | Anchor of a heavy argument block |
| [`A5`](A5.md) | Indented scope body |
| [`A6`](A6.md) | Signature `=` placement |
| [`A7`](A7.md) | `given` continuation alignment |
| [`A8`](A8.md) | Multi-line interpolated-string layout |
| [`A9`](A9.md) | Quote and splice layout |

## D — Density

*How much to fit on one line.*

| Rule | |
| --- | --- |
| [`D1`](D1.md) | Unnecessary line break |
| [`D2`](D2.md) | Lambda bracketing |

## C — Continuation Marking

*Showing that a line is unfinished.*

| Rule | |
| --- | --- |
| [`C1`](C1.md) | `.method` continuation lines |
| [`C2`](C2.md) | Symbolic infix operator continuation |
| [`C3`](C3.md) | Continuation indents after `=>` and heavy `:` |

## B — Balance

*Symmetry of spacing around a token.*

| Rule | |
| --- | --- |
| [`B1`](B1.md) | Operator spacing |
| [`B2`](B2.md) | Assignment spacing |
| [`B3`](B3.md) | Single space between specific tokens |
| [`B4`](B4.md) | Multi-line block bracket spacing |
| [`B5`](B5.md) | Bracket inner spacing |

## P — Proximity

*Blank lines and gaps as grouping.*

| Rule | |
| --- | --- |
| [`P1`](P1.md) | Spacing around commas |
| [`P2`](P2.md) | Blank-line spacing between sibling declarations |
| [`P3`](P3.md) | Too many consecutive blank lines |
| [`P4`](P4.md) | Blank line after heavy-signature return type |
| [`P5`](P5.md) | Blank line after imports |
| [`P6`](P6.md) | Annotations and following declarations |

## T — Tabulation

*Vertical alignment across a run of lines.*

| Rule | |
| --- | --- |
| [`T1`](T1.md) | `=>` alignment in case run |
| [`T2`](T2.md) | `using`-clause alignment |
| [`T3`](T3.md) | `for`-comprehension layout |

## L — Locatability

*Finding a definition from its name.*

| Rule | |
| --- | --- |
| [`L1`](L1.md) | Top-level import rules |
| [`L2`](L2.md) | File naming conventions |
| [`L3`](L3.md) | `object` must precede `class`/`trait`/`enum` of same name |
| [`L4`](L4.md) | Definition not exported to the umbrella package |
| [`L5`](L5.md) | Extension method not exported to the umbrella package |
| [`L6`](L6.md) | Name exported to the umbrella package by two components |

## S — Soundness

*What the code claims about itself.*

Unlike the other principles, these rules are about meaning rather than
layout, and they are inert until the project declares a gate — the token type
whose `using` parameter marks a definition as bypassing a guarantee, and the
prefix such a definition's name must carry.

| Rule | |
| --- | --- |
| [`S1`](S1.md) | Unsafe naming |

## Suppressing a rule

There is no per-site suppression. A rule that is wrong for a project is left
out of its style with `omit` (`omit L4`, or a whole principle with `omit S`),
and a rule that depends on a project parameter — the licence header, the
column limit, the umbrella package — is configured through the `style`
stanza of `.pyrocosm/flare/config.tel`, described in the
[README](../../.github/readme.md).

A project can also define rules of its own — named detections over the parse
tree, such as `rule cast` with `invokes asInstanceOf` — and enforce them
alongside the style; those are documented in the README rather than here,
since each project's are its own.

## Migrating from the `SN-` identifiers

These rules were previously numbered in the `SN-nnn` scheme, in which one
number could span several unrelated rules. The old identifiers map onto the
new ones as follows.

| Was | Is |
| --- | --- |
| `SN-799` | [`F1`](F1.md) |
| `SN-131` | [`F2`](F2.md) |
| `SN-658` | [`F3`](F3.md) |
| `SN-230` | [`F4`](F4.md) |
| `SN-135` | [`F5`](F5.md) |
| `SN-926` | [`F6`](F6.md) |
| `SN-015` | [`F7`](F7.md) |
| `SN-162` | [`F8`](F8.md) |
| `SN-162.1` | [`F8.1`](F8.md) |
| `SN-162.2` | [`F8.2`](F8.md) |
| `SN-473.1` | [`A1`](A1.md) |
| `SN-833` | [`A2`](A2.md) |
| `SN-833.1` | [`A2.1`](A2.md) |
| `SN-833.2` | [`A2.2`](A2.md) |
| `SN-833.3` | [`A3`](A3.md) |
| `SN-833.4` | [`A4`](A4.md) |
| `SN-473.8` | [`A5`](A5.md) |
| `SN-473.9` | [`A6`](A6.md) |
| `SN-140` | [`A7`](A7.md) |
| `SN-560` | [`A8`](A8.md) |
| `SN-560.1` | [`A8.1`](A8.md) |
| `SN-560.2` | [`A8.2`](A8.md) |
| `SN-560.3` | [`A8.3`](A8.md) |
| `SN-560.4` | [`A8.4`](A8.md) |
| `SN-473.2` | [`A9.1`](A9.md) |
| `SN-473.3` | [`A9.2`](A9.md) |
| `SN-473.4` | [`A9.3`](A9.md) |
| `SN-473.5` | [`A9.4`](A9.md) |
| `SN-473.6` | [`A9.5`](A9.md) |
| `SN-473.7` | [`A9.6`](A9.md) |
| `SN-247` | [`D1`](D1.md) |
| `SN-312` | [`D2`](D2.md) |
| `SN-312.1` | [`D2.1`](D2.md) |
| `SN-312.2` | [`D2.2`](D2.md) |
| `SN-312.3` | [`D2.3`](D2.md) |
| `SN-312.4` | [`D2.4`](D2.md) |
| `SN-163` | [`C1`](C1.md) |
| `SN-163.1` | [`C1.1`](C1.md) |
| `SN-163.2` | [`C1.2`](C1.md) |
| `SN-616` | [`C2`](C2.md) |
| `SN-616.1` | [`C2.1`](C2.md) |
| `SN-616.2` | [`C2.2`](C2.md) |
| `SN-616.3` | [`C2.3`](C2.md) |
| `SN-444` | [`C3`](C3.md) |
| `SN-376` | [`B1`](B1.md) |
| `SN-376.1` | [`B2`](B2.md) |
| `SN-013` | [`B3`](B3.md) |
| `SN-811` | [`B4`](B4.md) |
| `SN-402` | [`B5`](B5.md) |
| `SN-529` | [`P1`](P1.md) |
| `SN-529.1` | [`P1.1`](P1.md) |
| `SN-529.2` | [`P1.2`](P1.md) |
| `SN-315` | [`P2`](P2.md) |
| `SN-783` | [`P3`](P3.md) |
| `SN-677` | [`P4`](P4.md) |
| `SN-441` | [`P5`](P5.md) |
| `SN-551` | [`P6`](P6.md) |
| `SN-551.1` | [`P6.1`](P6.md) |
| `SN-551.2` | [`P6.2`](P6.md) |
| `SN-326` | [`T1`](T1.md) |
| `SN-946` | [`T2`](T2.md) |
| `SN-924` | [`T3`](T3.md) |
| `SN-924.1` | [`T3.1`](T3.md) |
| `SN-924.2` | [`T3.2`](T3.md) |
| `SN-924.3` | [`T3.3`](T3.md) |
| `SN-924.4` | [`T3.4`](T3.md) |
| `SN-302` | [`L1`](L1.md) |
| `SN-302.1` | [`L1.1`](L1.md) |
| `SN-302.2` | [`L1.2`](L1.md) |
| `SN-302.3` | [`L1.3`](L1.md) |
| `SN-847` | [`L2`](L2.md) |
| `SN-398` | [`L3`](L3.md) |
| `SN-742` | [`L4`](L4.md) |
| `SN-742.1` | [`L5`](L5.md) |
| `R33-multiline-pattern-arrow-position` | [`T1.1`](T1.md) |
| `R33-multiline-pattern-body-newline` | [`T1.2`](T1.md) |
| `R33-multiline-case-arrow-space` | [`T1.3`](T1.md) |
