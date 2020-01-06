MerkleDAG Core
==============

[![CircleCI](https://circleci.com/gh/greglook/merkledag-core.svg?style=shield&circle-token=27a8c9928a26b924edf4cd3247f0adf0364be4cc)](https://circleci.com/gh/greglook/merkledag-core)
[![codecov](https://codecov.io/gh/greglook/merkledag-core/branch/develop/graph/badge.svg)](https://codecov.io/gh/greglook/merkledag-core)
[![cljdoc lib](https://img.shields.io/badge/cljdoc-lib-blue.svg)](https://cljdoc.org/d/mvxcvi/merkledag-core)

This library implements a graph data storage layer out of linked structures of
content-addressed nodes.

Each node is a set of link and body data [flexibly encoded](https://github.com/multiformats/clj-multicodec)
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
; Require and alias core namespace:
=> (require '[merkledag.core :as mdag])

; Create a node store to hold the graph data:
=> (def graph (mdag/init-store))
```

By default, the `init-store` constructor produces a node store with in-memory
block storage, a simple EDN-based codec, and no parse cache. Now we can store
some data in it:

```clojure
=> (mdag/store-node! graph nil {:abc 123})
{:merkledag.node/id #data/hash "Qma2BhVGoacUWC9duYDBkLDNWohB3dfCHBpGQdZUJ8B5H7",
 :merkledag.node/size 38,
 :merkledag.node/encoding ["/merkledag/v1" "/edn"],
 :merkledag.node/data {:abc 123}}

=> (mdag/store-node! graph nil [true #{123 :x} 'efg])
{:merkledag.node/id #data/hash "Qmaarse6kx5tKmtsX1LgJ2B59Xi8bzEnH8uQ5MKMQJg3Pd",
 :merkledag.node/size 48,
 :merkledag.node/encoding ["/merkledag/v1" "/edn"],
 :merkledag.node/data [true #{123 :x} efg]}

=> (mdag/store-node! graph nil {:a (mdag/link "a" *2), :b (mdag/link "b" *1)})
{:merkledag.node/id #data/hash "QmVSR5TWZmKr3uXF8yBLPSE2ki25LKXkniGMNBCxCT8UUt",
 :merkledag.node/size 251,
 :merkledag.node/encoding ["/merkledag/v1" "/edn"],
 :merkledag.node/links
 [#merkledag/link ["a" #data/hash "Qma2BhVGoacUWC9duYDBkLDNWohB3dfCHBpGQdZUJ8B5H7" 38]
  #merkledag/link ["b" #data/hash "Qmaarse6kx5tKmtsX1LgJ2B59Xi8bzEnH8uQ5MKMQJg3Pd" 48]],
 :merkledag.node/data
 {:a #merkledag/link ["a" #data/hash "Qma2BhVGoacUWC9duYDBkLDNWohB3dfCHBpGQdZUJ8B5H7" 38],
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
=> (mdag/get-data graph "QmVSR5TWZmKr3uXF8yBLPSE2ki25LKXkniGMNBCxCT8UUt")
{:a #merkledag/link ["a" #data/hash "Qma2BhVGoacUWC9duYDBkLDNWohB3dfCHBpGQdZUJ8B5H7" 38],
 :b #merkledag/link ["b" #data/hash "Qmaarse6kx5tKmtsX1LgJ2B59Xi8bzEnH8uQ5MKMQJg3Pd" 48]}

; As do links themselves:
=> (mdag/get-data graph (:b *1))
[true #{123 :x} efg]
```

The real power of the link names comes up when you have more deeply nested
structures, and want to resolve paths through them:

```clojure
; Store a new top-level link-only node:
=> (mdag/store-node! graph [(mdag/link "link:0" "QmVSR5TWZmKr3uXF8yBLPSE2ki25LKXkniGMNBCxCT8UUt")] nil)
{:merkledag.node/id #data/hash "QmcFVHLW7vXYNowWH3DhmuNkvQnWW59ex7MutG8fcUuLXJ",
 :merkledag.node/size 120,
 :merkledag.node/encoding ["/merkledag/v1" "/edn"],
 :merkledag.node/links
 [#merkledag/link ["link:0" #data/hash "QmVSR5TWZmKr3uXF8yBLPSE2ki25LKXkniGMNBCxCT8UUt" nil]]}

; Look up data with a path:
=> (mdag/get-data graph *1 "link:0/a")
{:abc 123}

; We can also supply a 'not-found' value:
=> (mdag/get-data graph *2 "a/b/c" :foo/not-found)
:foo/not-found
```


## License

This is free and unencumbered software released into the public domain.
See the UNLICENSE file for more information.
