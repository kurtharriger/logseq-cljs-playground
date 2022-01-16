(ns cljs-playground.core
  (:require [applied-science.js-interop :as j]
            [cljs.core :refer [js->clj]]
            [cljs.core.async :as async :refer-macros [go] :refer [<! >!]]
            [cljs-http.client :as http]
            [cljs.reader :refer [read-string]]
            [clojure.pprint :refer [pprint]]
            [clojure.string]
            [com.rpl.specter :as s :refer-macros [select transform]]
            [datascript.core :as d]
            [promesa.core :as p]))

(js/logseq.App.showMsg "Hello from Clojure!")

(defn main []
  (js/logseq.App.showMsg "Hello from Clojure!"))

(defn init []
  (-> (js/logseq.ready main)
      (.catch js/console.error)))

(def page-name "Things")

(defn cljify [obj & args]
  (let [jsargs (mapv clj->js args)]
    (-> (apply (partial j/call obj) args)
        (p/then #(js->clj % :keywordize-keys true)))) )

(def get-page (partial cljify js/logseq.Editor :getPage))
(def create-page (partial cljify js/logseq.Editor :createPage))
(def insert-block (partial cljify js/logseq.Editor :insertBlock))
(def insert-batch-block (partial cljify js/logseq.Editor :insertBatchBlock))

(defn things-page [] 
  (p/let [page (get-page page-name)]
         (if page page (create-page page-name))))

(defn printp [promise] (p/then promise prn))

(def datascriptQuery* (partial cljify js/logseq.DB :datascriptQuery))

(defn q [query]
  (datascriptQuery* (pr-str query)))


;; (def get-page [& args])
;; (defn jcall []
;;   (-> (p/call js/logseq.Editor :getPage page-name)))

;; (defn get-things-page []
;;   (p/let [page (p/call js/logseq.Editor :getPage page-name)]
;;     (if page))
;;   (-> (p/call js/logseq.Editor :getPage "Things")

;;       (p/then (fn [page]))))




  (defonce data (atom nil))

;; (def schemaless- db (d/create-conn {}))
  
(defonce db
  (d/create-conn
   {:things.area/uuid {:db/unique :db.unique/identity}
    :things.task/uuid {:db/unique :db.unique/identity}
    :things.checklist/uuid  {:db/unique :db.unique/identity}
    ;:things.project/uuid {:db/unique :db.unique/identity}
    :things.task/area {:db/valueType :db.type/ref}
    ;:things.task/project {:db/valueType :db/ref}
    :things.checklist/task {:db/valueType :db.type/ref}}))

  (defn update-refs [data]
    (->> data
         (transform [s/ALL (s/must :things.task/area)] #(do [:things.area/uuid %]))
         (transform [s/ALL (s/must :things.task/project)] #(do [:things.project/uuid %]))
         (transform [s/ALL (s/must :things.checklist/task)] #(do [:things.task/uuid %]))))
  
(comment
  ; (js/logseq.App.showMsg "hi")
  ; 
  (-> (j/call js/logseq.Editor :getCurrentBlock)
      (p/then #(js->clj % :keywordize-keys true))
      (p/then println))

  ;(j/call js/logseq.Editor :insertAtEditingCursor "TODO new task")
  (j/call js/logseq.Editor :insertBlock)
  ; logseq.api.insert_batch_block(logseq.api.get_current_block().uuid, [{content: "testing5", children: [{content: "child"}]}], {sibling: true})
  ; default for sibling is true
  ; false makes it a child

  ;(insert-batch-block (things-page) [{:content "Work"}, {:content "Family"}], {:sibling false})
  ;[:find (pull ?e [*]) :where [?e :block/scheduled ?d]]
  ;(printp (j/call js/logseq.DB :datascriptQuery "[:find (pull ?e [*]) :where [?e :block/scheduled ?d]]"))
  (printp (q '[:find (pull ?e [*]) :where [?e :block/scheduled ?d]]))

   

  (go (reset! data   (:body (<! (http/get "http://localhost:7980")))))
  
  (swap! data update-refs)
  
  (do (d/transact! db (take 20 @data)) nil)
  (do (d/transact! db @data) nil)

  (-> (d/q '[:find ?e  :where [?e :things.area/uuid]] @db)
      count)
  (-> (d/q '[:find ?e  :where [?e :things.task/uuid]] @db)
      count)
  (-> (d/q '[:find ?e  :where [?e :things.checklist/uuid]] @db)
      count)


  (-> (d/q  '[:find (pull ?a [*]) .
              :where [?a :things.area/title "Family"]]
            @db)
      pprint)
  
  (-> (d/q  '[:find (pull ?t [:things.task/title])
              :where
              [?a :things.area/title "Family"]
              ;[?a :things.area/uuid ?auuid]
              ;[?t :things.task/area ?auuid]
              [?t :things.task/status 0]]
            @db)
      pprint)
  

  (-> (d/q  '[:find (pull ?t [*])
              :where
              [?a :things.area/title "Family"]
              [?t :things.task/area ?a]]
            @db)
  )
)

  