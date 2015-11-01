## codec/decode -> MerkleLink

Decoding a blob (`codec/decode`) needs to know how to construct the right type
when parsing links, so it needs to be able to construct `MerkleLink` values. It
can handle the overall node okay because `Blob` is from a separate library.


## graph/graph-repo -> data/core-types

Newly constructed code should use the built-in `core-types` by default.


## MerkleLink/deref -> \*get-node\*

MerkleLink's IDeref now uses a local dynamic var to point at the function to use
to resolve links.


## graph/get-node -> codec/decode

When retrieving nodes from the blob store, they need to be _decoded_ in order to
parse the links and dta in them.
