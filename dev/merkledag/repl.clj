(ns merkledag.repl
  (:require
    [blocks.core :as block]
    [blocks.store.file :refer [file-block-store]]
    [blocks.store.memory :refer [memory-block-store]]
    [clojure.java.io :as io]
    [clojure.repl :refer :all]
    [clojure.stacktrace :refer [print-cause-trace]]
    [clojure.string :as str]
    [clojure.tools.namespace.repl :refer [refresh]]
    [merkledag.core :as mdag]
    [merkledag.link :as link]
    [merkledag.node :as node]
    [multiformats.hash :as multihash]))


#_
(def graph
  (mdag/init-store
    :store (file-block-store "dev/data/blocks")
    :cache {:total-size-limit (* 16 1024)}))
