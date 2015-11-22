Clojure Merkle-DAG
==================

**WORK IN PROGRESS**

This library implements a simplified version of the
[IPFS](//github.com/ipfs/ipfs) merkle-dag data layer. This builds on
[content-addressable block storage](//github.com/greglook/blocks) with a codec
to translate the Merkle-DAG data structure into serialized block data.

## Concepts

- A [multihash](https://github.com/greglook/clj-multihash) is a value specifying
  a hashing algorithm and a digest.
- A _block_ is a sequence of bytes, identified by a multihash.
- Blocks can be referenced by _merkle links_, which have a string name, a
  multihash target, and a referred size.
- A _node_ is a block following a certain format which encodes a table of merkle
  links and a data segment containing some other information.
- The data segment is serialized with a
  [multicodec](//github.com/greglook/clj-multicodec) header to make the encoding
  discoverable and upgradable.

Using these concepts, we can build a directed acyclic graph of nodes referencing
other nodes through merkle links.

## Node Format

...

## License

This is free and unencumbered software released into the public domain.
See the UNLICENSE file for more information.
