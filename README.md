Clojure MerkleDAG
=================

This library implements a simplified version of the
[IPFS](//github.com/ipfs/ipfs) merkle-dag data layer. This combines
[content-addressable block storage](//github.com/greglook/blocks) with a [set of
codecs](//github.com/greglook/clj-multicodec) to translate between the
Merkle-DAG data structure and serialized blocks.

## Concepts

- A [multihash](https://github.com/greglook/clj-multihash) is a value specifying
  a hashing algorithm and a digest.
- A _block_ is a sequence of bytes, identified by a multihash.
- Blocks can be referenced by _merkle links_, which have a string name, a
  multihash target, and a referred size.
- A _node_ is a block following a certain format which encodes a table of merkle
  links and a data segment containing some other information.
- The node _format_ defines how the links and data are serialized into a block.
  Links have a well-defined structure, while node data uses a variable set of
  codecs based on data type.
- The data segment is serialized with a
  [multicodec](//github.com/greglook/clj-multicodec) header to make the encoding
  discoverable and upgradable.

Using these concepts, we can build a directed acyclic graph of nodes referencing
other nodes through merkle links.

## API

The API for this library needs to support:
- Constructing a graph interface with:
  - Choice of type plugins to support new data types.
  - Choice of data codecs. (text, JSON, EDN, CBOR, etc)
- Creating links to multihash targets.
- Creating new node blocks without storing them.
- Storing blocks (both nodes and raw) in the graph.
- Retrieving a node/block from the graph by multihash id.

```clojure
(require
  '[merkledag.core :as merkle]
  '[merkledag.graph :as graph])

(def graph (graph/block-graph))

(graph/with-context graph
  (let [hash-1 (multihash/decode "Qmb2TGZBNWDuWsJVxX7MBQjvtB3cUc4aFQqrST32iASnEh")
        node-1 (merkle/node
                 {:type :finance/posting
                  :uuid "foo-bar"})
        node-2 (merkle/node
                 {:type :finance/posting
                  :uuid "frobblenitz omnibus"})
        node-3 (merkle/node
                 [(merkle/link "@context" hash-1)]
                 {:type :finance/transaction
                  :uuid #uuid "31f7dd72-c7f7-4a15-a98b-0f9248d3aaa6"
                  :title "Gas Station"
                  :description "Bought a pack of gum."
                  :time #inst "2013-10-08T00:00:00"
                  :entries [(merkle/link "posting-1" node-1)
                            (merkle/link "posting-2" node-2)]})]
    (merkle/put-node! graph node-1)
    (merkle/put-node! graph node-2)
    (merkle/put-node! graph node-3))
```

Now that the graph has some data, we can ask the graph for nodes back:

```clojure
; Nodes are Block values with :links and :data entries:
=> (merkle/get-node graph (:id node-3))
#blocks.data.Block
{:data {:description "Bought a pack of gum.",
        :entries [#data/link ["posting-1" #data/hash "QmYUJXaPqsreTj8wfxxeYfbi1cPAh7j434LxVSFB2ucPUQ" 49]
                  #data/link ["posting-2" #data/hash "QmTJaJRFW45X6JfJPDoXbjRHuRKuJN5YPEq3PG4XHvcZoS" 61]],
        :time #inst "2013-10-08T00:00:00.000Z",
        :title "Gas Station",
        :type :finance/transaction,
        :uuid #uuid "31f7dd72-c7f7-4a15-a98b-0f9248d3aaa6"},
 :id #data/hash "QmbbRoCQAzvZFjJGupbzKfqWRkLR6HxfxEZmDpw2Kjkqc7",
 :links [#data/link ["@context" #data/hash "Qmb2TGZBNWDuWsJVxX7MBQjvtB3cUc4aFQqrST32iASnEh" nil]
         #data/link ["posting-1" #data/hash "QmYUJXaPqsreTj8wfxxeYfbi1cPAh7j434LxVSFB2ucPUQ" 49]
         #data/link ["posting-2" #data/hash "QmTJaJRFW45X6JfJPDoXbjRHuRKuJN5YPEq3PG4XHvcZoS" 61]],
 :size 397}

; We can create a new link to this in turn, automatically calculating the total size:
=> (merkle/link *1)
#data/link ["tx" #data/hash "QmbbRoCQAzvZFjJGupbzKfqWRkLR6HxfxEZmDpw2Kjkqc7" 507]

; Resolving links in the context of a graph looks up the target:
=> (graph/with-context graph (= node-3 (deref *1)))
true
```

## License

This is free and unencumbered software released into the public domain.
See the UNLICENSE file for more information.
