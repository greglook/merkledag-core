MerkleDAG Core
==============

[![CircleCI](https://circleci.com/gh/greglook/merkledag-core.svg?style=shield&circle-token=27a8c9928a26b924edf4cd3247f0adf0364be4cc)](https://circleci.com/gh/greglook/merkledag-core)
[![codecov](https://codecov.io/gh/greglook/merkledag-core/branch/develop/graph/badge.svg)](https://codecov.io/gh/greglook/merkledag-core)
[![API documentation](https://img.shields.io/badge/doc-API-blue.svg)](https://greglook.github.io/merkledag-core/api/)
[![Literate documentation](https://img.shields.io/badge/doc-marginalia-blue.svg)](https://greglook.github.io/merkledag-core/marginalia/uberdoc.html)

This library implements a graph data storage layer out of linked structures of
content-addressed nodes.

Each node is a set of link and body data [flexibly encoded](https://github.com/greglook/clj-multicodec)
into an [immutable block of content](https://github.com/greglook/blocks).
Each block is addressed by the hash of its content, which includes the
serialized links. This forms into an expanded [Merkle tree](https://en.wikipedia.org/wiki/Merkle_tree),
which can model any directed acyclic graph - hence the name, MerkleDAG.


## Installation

Library releases are published on Clojars. To use the latest version with
Leiningen, add the following dependency to your project definition:

[![Clojars Project](http://clojars.org/mvxcvi/clj-cbor/latest-version.svg)](http://clojars.org/mvxcvi/clj-cbor)


## Concepts

- A [multihash](https://github.com/multiformats/clj-multihash) is a self-describing
  value specifying a cryptographic hashing algorithm and a digest.
- A [block](https://github.com/greglook/blocks) is a sequence of bytes, identified by
  a multihash of its content.
- Blocks can be referenced by _merkle links_, which have a multihash target and
  an optional name and reference size.
- A _node_ is a block [encoded](https://github.com/multiformats/clj-multicodec)
  in a structured format which contains a table of merkle links and a data
  value.
- Using named merkle links, we can construct a _merkle path_ from one node to
  another by following the named link for each path segment.

Using these concepts, we can build a directed acyclic graph of nodes referencing
each other through merkle links. The data structure formed from a given root
node is immutable, much like the collections in Clojure itself. When updates are
applied, they create a _new_ root node which structurally shares all of the
unchanged data with the old version.


## Usage

...


## License

This is free and unencumbered software released into the public domain.
See the UNLICENSE file for more information.
