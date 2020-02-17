(ns repository.publication
  (:require [korma.core :as db]
            [clj-uuid :as uuid]
            [utils.error-handler :as error-handler]
            [repository.status :as status])
  (:use [utils.string]))

(declare publications)

(defn get-publication-model
  [connection]
  (db/defentity publications
                (db/pk :id)
                (db/table :publication)
                (db/database connection)
                (db/entity-fields
                  :id
                  :legacy_id
                  :hash
                  :text
                  :screen_name
                  :avatar_url
                  :document_id
                  :document
                  :published_at))
  publications)

(defn select-publications
  [model]
  (->
    (db/select* model)
    (db/fields [:id :id]
               [:legacy_id :legacy-id]
               [:hash :hash]
               [:text :text]
               [:screen_name :screen-name]
               [:avatar_url :avatar-url]
               [:document_id :document-id]
               [:document :document]
               [:published_at :published-at])))

(defn find-publications-having-column-matching-values
  "Find publications which values of a given column
  can be found in collection passed as argument"
  [column values model]
  (let [values (if values values '(0))
        matching-records (-> (select-publications model)
                             (db/where {column [in values]})
                             (db/select))]
    (if matching-records
      matching-records
      '())))


(defn find-publications-by-ids
  [ids model]
  (find-publications-having-column-matching-values :id ids model))

(defn find-publications-by-document-ids
  [ids model]
  (find-publications-having-column-matching-values :document_id ids model))

(defn is-subset-of
  [publications-status-ids-set]
  (fn [publication]
    (let [document-id (:document_id publication)]
      (clojure.set/subset? #{document-id} publications-status-ids-set))))

(defn bulk-insert-of-publication-props
  [publication-props model status-model]
  (let [identified-props (pmap
                           #(assoc % :id (uuid/to-string
                                           (-> (uuid/v1) (uuid/v5 (:legacy_id %)))))
                           publication-props)
        publication-status-ids (pmap #(:document_id %) identified-props)
        existing-publications (find-publications-by-document-ids publication-status-ids model)
        existing-publication-status-ids (pmap #(:document_id %) existing-publications)
        filtered-publications (doall (remove (is-subset-of (set existing-publication-status-ids)) identified-props))
        publication-ids (pmap #(:id %) filtered-publications)]
    (if publication-ids
      (do
        (try
          (db/insert model (db/values filtered-publications))
          (catch Exception e
            (error-handler/log-error e)))
        (find-publications-by-ids publication-ids model))
      '())
    (if publication-status-ids
      (status/update-status-having-status-ids publication-status-ids status-model))))