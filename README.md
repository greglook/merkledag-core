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

[![Clojars Project](http://clojars.org/mvxcvi/merkledag-core/latest-version.svg)](http://clojars.org/mvxcvi/merkledag-core)


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

This is the core library, so it provides a direct interface for working with the
nodes in a graph.

```clojure
; Require and alias core namespaces:
=> (require '[merkledag.link :as link] '[merkledag.node :as node])

; Create a node store to hold the graph data:
=> (require '[merkledag.system.util :as msu])
=> (def graph (msu/init-store))
```

By default, the `init-store` constructor produces a node store with in-memory
block storage, a simple EDN-based codec, and no parse cache. Now we can store
some data in it:

```clojure
=> (node/store-node! graph nil {:abc 123})
{:merkledag.node/id #data/hash "Qma2BhVGoacUWC9duYDBkLDNWohB3dfCHBpGQdZUJ8B5H7",
 :merkledag.node/size 38,
 :merkledag.node/encoding ["/merkledag/v1" "/edn"],
 :merkledag.node/data {:abc 123}}

=> (node/store-node! graph nil [true #{123 :x} 'efg])
{:merkledag.node/id #data/hash "Qmaarse6kx5tKmtsX1LgJ2B59Xi8bzEnH8uQ5MKMQJg3Pd",
 :merkledag.node/size 48,
 :merkledag.node/encoding ["/merkledag/v1" "/edn"],
 :merkledag.node/data [true #{123 :x} efg]}

=> (node/store-node! graph nil {:a (link/link-to "a" *2), :b (link/link-to "b" *1)})
{:merkledag.node/id #data/hash "QmVSR5TWZmKr3uXF8yBLPSE2ki25LKXkniGMNBCxCT8UUt",
 :merkledag.node/size 251,
 :merkledag.node/encoding ["/merkledag/v1" "/edn"],
 :merkledag.node/links [#merkledag/link ["a" #data/hash "Qma2BhVGoacUWC9duYDBkLDNWohB3dfCHBpGQdZUJ8B5H7" 38]
                        #merkledag/link ["b" #data/hash "Qmaarse6kx5tKmtsX1LgJ2B59Xi8bzEnH8uQ5MKMQJg3Pd" 48]],
 :merkledag.node/data {:a #merkledag/link ["a" #data/hash "Qma2BhVGoacUWC9duYDBkLDNWohB3dfCHBpGQdZUJ8B5H7" 38],
                       :b #merkledag/link ["b" #data/hash "Qmaarse6kx5tKmtsX1LgJ2B59Xi8bzEnH8uQ5MKMQJg3Pd" 48]}}
```

We've now stored a basic three-node structure in the graph! Note that the last
node has links to each of the initial nodes, forming a simple binary tree. The
links were captured in the node's link table automatically by detecting them in
the node data body.

Later, node data can be retrieved from the graph by using a value which can be
linked to a node id.

```clojure
; Basic sting-encoded multihashes work:
=> (node/get-data graph "QmVSR5TWZmKr3uXF8yBLPSE2ki25LKXkniGMNBCxCT8UUt")
{:a #merkledag/link ["a" #data/hash "Qma2BhVGoacUWC9duYDBkLDNWohB3dfCHBpGQdZUJ8B5H7" 38],
 :b #merkledag/link ["b" #data/hash "Qmaarse6kx5tKmtsX1LgJ2B59Xi8bzEnH8uQ5MKMQJg3Pd" 48]}

; As do links themselves:
=> (node/get-data graph (:b *1))
[true #{123 :x} efg]
```


## License

This is free and unencumbered software released into the public domain.
See the UNLICENSE file for more information.
