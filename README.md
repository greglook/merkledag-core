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

## Usage

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
```

## License

This is free and unencumbered software released into the public domain.
See the UNLICENSE file for more information.
