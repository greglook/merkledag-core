Clojure MerkleDAG
=================

This library implements a simplified version of the
[IPFS](https://github.com/ipfs/ipfs) MerkleDAG data layer. This combines
[content-addressable block storage](https://github.com/greglook/blocks) with a
[set of codecs](https://github.com/greglook/clj-multicodec) to translate between
the graph data structure and serialized blocks.

The name comes from the observation that a collection of blocks where some
blocks contain links to other blocks forms a [directed acyclic
graph](https://en.wikipedia.org/wiki/Directed_acyclic_graph) (DAG). Nodes are
labeled by the hash of their contents, forming an expanded version of a [Merkle
tree](https://en.wikipedia.org/wiki/Merkle_tree). Hence the combined name,
merkledag.

This is currently **work in progress**. Stay tuned for updates!

## Concepts

- A [multihash](https://github.com/greglook/clj-multihash) is a self-describing
  value specifying a cryptographic hashing algorithm and a digest.
- A [block](https://github.com/greglook/blocks) is a sequence of bytes, identified by
  a multihash of its content.
- Blocks can be referenced by _merkle links_, which have a multihash target and
  an optional name and reference size.
- A _node_ is a block [encoded](https://github.com/greglook/clj-multicodec) in a
  structured format which contains a table of merkle links and a data value.
- Using named merkle links, we can construct a _merkle path_ from one node to
  another by following the named link for each path segment.
- A _ref_ is a named mutable pointer to a node, and serves as the 'roots' of a
  repository.

Using these concepts, we can build a directed acyclic graph of nodes referencing
each other through merkle links. The data-web structure formed from a given root
node is immutable, much like the collections in Clojure itself. When updates are
applied, they create a _new_ root node which structurally shares all of the
unchanged data with the old version.

## API

This library needs to support:

- Extensible codec system to support new block encodings, e.g. `text`, `json`,
  `edn`, `cbor`, etc.
- Type plugin system to support new data type extensions in nodes. Examples:
  * time values (instants, dates, intervals)
  * byte sequences (raw, direct, chunk trees)
  * unit quantities (physical units)
  This will need to support codec-dependent configuration; for example, optimal
  CBOR representations will differ from the naive encoding of EDN-style tagged
  literals.
- Creating links to multihash targets.
- Creating new node blocks without storing them.
- Storing blocks (both nodes and raw) in the graph.
- Retrieving a node/block from the graph by multihash id.
- Path resolution from a root node using link names.
- Data-web structure helpers like `assoc`, `assoc-in`, `update`, `update-in`,
  etc.

## License

This is free and unencumbered software released into the public domain.
See the UNLICENSE file for more information.
