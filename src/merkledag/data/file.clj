(ns merkledag.data.file
  "Support for common data attributes."
  (:require
    [merkledag.data.common :as common]
    [merkledag.data.content :as content]
    [merkledag.data.time :as time]
    [schema.core :as s])
  (:import
    merkledag.link.MerkleLink
    org.joda.time.DateTime))


(def FileMetadata
  (merge common/CommonAttributes
         {:file/name s/Str
          :file/permissions s/Str
          :file/owner s/Str
          :file/owner-id s/Int
          :file/group s/Str
          :file/group-id s/Int
          :file/create-time time/inst-schema
          :file/modify-time time/inst-schema}))


(s/defschema FileSchema
  (merge FileMetadata
         content/ContentAttributes))


(s/defschema DirectorySchema
  (assoc FileMetadata
         :group/children #{MerkleLink}))


(s/defschema LinkSchema
  (assoc FileMetadata
         :file.link/target s/Str))


(def data-attributes
  {:file/name
   {:schema s/Str}

   :file/permissions
   {:schema s/Str}

   :file/owner
   {:schema s/Str}

   :file/owner-id
   {:schema s/Int}

   :file/group
   {:schema s/Str}

   :file/group-id
   {:schema s/Int}

   :file/change-time
   {:schema DateTime}

   :file/modify-time
   {:schema DateTime}})
