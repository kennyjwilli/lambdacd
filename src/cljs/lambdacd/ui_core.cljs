(ns lambdacd.ui-core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs.core.async :as async]
            [lambdacd.utils :as utils :refer [classes]]
            [lambdacd.api :as api]
            [lambdacd.pipeline :as pipeline]
            [lambdacd.route :as route]
            [lambdacd.history :as history]
            [lambdacd.commons :as commons]
            [lambdacd.output :as output]))

(enable-console-print!)

(def poll-frequency 1000)

(defn poll [atom connection-lost-atom fn]
  (go-loop []
    (let [new-val (async/<! (fn))]
      (if new-val
        (do
          (reset! connection-lost-atom false)
          (reset! atom new-val))
        (reset! connection-lost-atom true))
      )
    (async/<! (utils/timeout poll-frequency))
    (recur)))

(defn poll-history [history-atom connection-lost-atom]
  (poll history-atom connection-lost-atom api/get-build-history))

(defn poll-state [state-atom build-number-atom connection-lost-atom]
  (poll state-atom connection-lost-atom #(api/get-build-state @build-number-atom)))

(defn current-build-header-component [build-number]
  [:h2 (str "Current Build " build-number)])

(defn current-build-component [build-state-atom build-number step-id-to-display-atom output-details-visible pipeline-component output-component header-component]
  (if-not (nil? @build-state-atom)
    [:div {:class "build" :key build-number}
     [header-component build-number]
     [pipeline-component build-number build-state-atom @step-id-to-display-atom]
     [output-component @build-state-atom @step-id-to-display-atom output-details-visible]]
    [commons/loading-screen]))

(defn wired-current-build-component [build-state-atom build-number step-id-to-display-atom output-details-visible]
  [current-build-component build-state-atom build-number step-id-to-display-atom output-details-visible pipeline/pipeline-component output/output-component current-build-header-component])

(defn header []
  [:div
   [:h1 "LambdaCD"]])

(defn root [build-number-atom step-id-to-display-atom history state output-details-visible connection-lost history-component current-build-component header-component]
  (let [build-number @build-number-atom
        container-classes (if @connection-lost
                            ["app" "l-horizontal" "app--connection-lost"]
                            ["app" "l-horizontal"] )]
    (if build-number
      [:div {:class (classes container-classes)}
       [:div {:class "l-vertical app__content"}
         [:div {:id "builds" :class "app__history l-vertical"} [history-component @history build-number]]
         [:div {:id "currentBuild" :class "app__current-build l-vertical"} [current-build-component state build-number step-id-to-display-atom output-details-visible]]]]
      [:div {:id "loading"}
       [:h1 "Loading..."]])))


(defn init! []
  (let [build-number-atom (atom nil)
        step-id-to-display-atom (atom nil)
        history-atom (atom nil)
        state-atom (atom nil)
        output-details-visible (atom false)
        connection-lost-atom (atom false)]
    (poll-history history-atom connection-lost-atom)
    (poll-state state-atom build-number-atom connection-lost-atom)
    (route/hook-browser-navigation! build-number-atom step-id-to-display-atom state-atom)
    ; #' is necessary so that fighweel can update: https://github.com/reagent-project/reagent/issues/94
    (reagent/render-component [#'root build-number-atom step-id-to-display-atom history-atom state-atom output-details-visible connection-lost-atom history/build-history-component wired-current-build-component header] (.getElementById js/document "app"))))


