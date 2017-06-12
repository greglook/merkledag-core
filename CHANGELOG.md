Change Log
==========

All notable changes to this project will be documented in this file, which
follows the conventions of [keepachangelog.com](http://keepachangelog.com/).
This project adheres to [Semantic Versioning](http://semver.org/).

## [Unreleased]

This is a breaking change which moves much of the library API into a single
`merkledag.core` namespace.

### Changed
- Many functions moved to `merkledag.core` namespace.
- Symbol tags for the `MerkleLink` and `LinkIndex` types changed to
  `merkledag/link` (from `data/link`) and `merkledag.link/index` (from
  `data/link-index`). This is a breaking change for already-stored EDN data, but
  can be backwards compatible by declaring readers for the old tags.
- Node store methods consistently return maps, not blocks.

## [0.1.0] - 2017-06-10

Initial project release.

[Unreleased]: https://github.com/greglook/merkledag-core/compare/0.1.0...HEAD
[0.1.0]: https://github.com/greglook/merkledag-core/tag/0.1.0
