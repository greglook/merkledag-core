Change Log
==========

All notable changes to this project will be documented in this file, which
follows the conventions of [keepachangelog.com](http://keepachangelog.com/).
This project adheres to [Semantic Versioning](http://semver.org/).

## [Unreleased]

...

## [0.3.2] - 2017-11-05

### Changed
- Various minor dependency upgrades.

## [0.3.1] - 2017-09-13

### Added
- The return value of `store-node!` annotates the data and links with the
  corresponding node metadata.
- Values are checked for node metadata when used for link targeting.

## [0.3.0] - 2017-08-03

### Added
- MerkleLink attributes can be accessed with either simple or namespace
  qualified keywords, meaning both `(::link/target x)` and `(:target x)` return
  the same value.
- Nodes are always returned with their link table and data values decorated with
  node-level metadata, so that `(::node/data (get-node store x))` has the same
  result as `(get-data store x)`.

### Breaking
- CBOR tag ids for Multihash and MerkleLink values changed to 422 and 423,
  respectively.
- Moved merkle-link predicate from `merkledag.link/merkle-link?` to
  `merkledag.core/link?`.
- `get-node`, `get-links`, and `get-data` now have a consistent set of 2-4 arity
  implementations to accept an optional `path` and `not-found` value.

## [0.2.0] - 2017-06-11

This is a breaking change which moves much of the library API into a single
`merkledag.core` namespace.

### Breaking
- Many functions moved to `merkledag.core` namespace.
- Symbol tags for the `MerkleLink` and `LinkIndex` types changed to
  `merkledag/link` (from `data/link`) and `merkledag.link/index` (from
  `data/link-index`). This is a breaking change for already-stored EDN data, but
  can be backwards compatible by declaring readers for the old tags.
- Node store methods consistently return maps, not blocks.

## [0.1.0] - 2017-06-10

Initial project release.

[Unreleased]: https://github.com/greglook/merkledag-core/compare/0.3.2...HEAD
[0.3.2]: https://github.com/greglook/merkledag-core/compare/0.3.1...0.3.2
[0.3.1]: https://github.com/greglook/merkledag-core/compare/0.3.0...0.3.1
[0.3.0]: https://github.com/greglook/merkledag-core/compare/0.2.0...0.3.0
[0.2.0]: https://github.com/greglook/merkledag-core/compare/0.1.0...0.2.0
[0.1.0]: https://github.com/greglook/merkledag-core/tag/0.1.0
