Clojure MerkleDAG
=================

This library implements a simplified version of the
[IPFS](//github.com/ipfs/ipfs) merkle-dag data layer. This combines
[content-addressable block storage](//github.com/greglook/blocks) with a [set of
codecs](//github.com/greglook/clj-multicodec) to translate between the
Merkle-DAG data structure and serialized blocks.

This is currently **work in progress**. Stay tuned for updates!

## Concepts

- A [multihash](//github.com/greglook/clj-multihash) is a self-describing
  value specifying a cryptographic hashing algorithm and a digest.
- A [block](//github.com/greglook/blocks) is a sequence of bytes, identified by
  a multihash of its content.
- Blocks can be referenced by _merkle links_, which have a multihash target and
  an optional name and reference size.
- A _data block_ is serialized with a self-describing
  [multicodec](//github.com/greglook/clj-multicodec) header to make the encoding
  discoverable and upgradable. All other blocks are considered 'raw' blocks with
  opaque binary content.
- A _node_ is a data block encoded with a specific format which records a table
  of merkle links and a value as structured data. Some potential encodings for
  this are EDN and CBOR.
- A _ref_ is a named mutable pointer to a node.

Using these concepts, we can build a directed acyclic graph of nodes referencing
each other through merkle links. The data-web structure formed from a given root
node is immutable, much like the collections in Clojure itself. When updates are
applied, they create a _new_ root node which structurally shares all of the
unchanged data with the old version.

## API

This library needs to support:

- Extensible codec system to support new block encodings, e.g. `bin`, `text`,
  `json`, `edn`, `cbor`, etc.
- Type plugin system to support new data type extensions in nodes. This is
  generally going to be codec-dependent, for example optimal CBOR
  representations would differ from the naive encoding of EDN-style tagged
  literals. Examples:
  - time values (instants, dates, intervals)
  - byte sequences (raw, direct, chunk trees)
  - unit quantities (physical units)
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
