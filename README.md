Clojure Merkle-DAG
==================

**WORK IN PROGRESS**

This repo implements a repository of Merkle-DAG objects. This is essentially a
simplified version of [IPFS](https://github.com/ipfs/ipfs) without the sharing
or distributed parts.

## Concepts

- A [multihash](https://github.com/jbenet/multihash) is a _value_ describing a
  hashing algorithm and a digest.
- A _blob_ is a sequence of bytes, identified by a multihash address.

## License

This is free and unencumbered software released into the public domain.
See the UNLICENSE file for more information.
