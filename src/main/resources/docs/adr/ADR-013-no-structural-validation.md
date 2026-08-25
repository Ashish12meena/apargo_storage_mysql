# ADR-013 — No structural file validation

**Status:** Accepted · 2026-08-22

## Context

An earlier draft planned structural limits for Phase 4: image pixel counts, PDF
page counts, archive rejection. The stated rationale was that byte size alone does
not bound resource consumption — a 100,000 × 100,000 PNG sits under a 50 MB limit
and expands to tens of gigabytes when decoded.

## Decision

**Not implemented.** The service validates size and content type. It does not
parse document internals.

## Consequences

- One fewer dependency surface. Structural inspection means image decoders and PDF
  parsers, which are large, historically CVE-prone, and themselves attack surface
  for the very files they inspect.
- The decompression-bomb argument does not apply here, because **nothing in this
  service decodes these files.** Bytes are stored and returned as-is. A pixel bomb
  is inert unless something renders it.
- `tika-core` remains — detection only, no parsers. It identifies formats; it does
  not open them.

## Revisit when

Something downstream starts decoding stored files: a thumbnail pipeline, a preview
renderer, an OCR step. At that point the limit belongs in **that** component, where
the decoding actually happens and the resource bound is meaningful — not here.

## Alternatives rejected

**Implement it now, defensively.** Building a bound for a risk that does not exist
yet, in the wrong component, at the cost of a parser dependency.
