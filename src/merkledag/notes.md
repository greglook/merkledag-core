## codec/decode -> MerkleLink

Decoding a blob (`codec/decode`) needs to know how to construct the right type
when parsing links, so it needs to be able to construct `MerkleLink` values. It
can handle the overall node okay because `Blob` is from a separate library.


## data/core-types -> MerkleLink, graph/link

The `core-types` map defines a rendered representation for `MerkleLink` values,
so it must be able to resolve the link type. This _could_ be added later on in
the `graph` namespace if necessary.


## graph/graph-repo -> data/core-types

Newly constructed code should use the built-in `core-types` by default.


## MerkleLink/deref -> graph/get-node, graph/\*graph-repo\*

In order to provide auto-fetching behavior via `IDeref`, the link type must be
able to call `get-node` against the currently-bound `*graph-repo*`. An
alternative might be to bind a function on `Multihash -> Blob`. Unfortunately,
`IDeref` must be implemented _inline_ in the `deftype` because it's an
interface, not a protocol.


## graph/get-node -> codec/decode

When retrieving nodes from the blob store, they need to be _decoded_ in order to
parse the links and dta in them.
