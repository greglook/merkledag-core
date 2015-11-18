Clojure Merkle-DAG
==================

**WORK IN PROGRESS**

This library implements a simplified version of the
[IPFS](//github.com/ipfs/ipfs) thin-waist data layer. This builds on
[content-addressable block storage](//github.com/greglook/blocks) with a codec
to translate the Merkle-DAG data structure into serialized block data.

## Concepts

- A [multihash](https://github.com/greglook/clj-multihash) is a _value_ describing a
  hashing algorithm and a digest.
- A _block_ is a sequence of bytes, identified by a multihash identifier.
- Blocks can be referenced by _merkle links_, which have a string name and a
  multihash target.
- Finally a _node_ is a block encoded with an (optional) table of links and an
  (optional) data segment containing some other data values.

## Node Encoding

...

## License

This is free and unencumbered software released into the public domain.
See the UNLICENSE file for more information.
