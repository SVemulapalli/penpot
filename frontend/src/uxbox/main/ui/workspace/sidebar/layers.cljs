;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) 2015-2016 Andrey Antukh <niwi@niwi.nz>
;; Copyright (c) 2015-2016 Juan de la Cruz <delacruzgarciajuan@gmail.com>

(ns uxbox.main.ui.workspace.sidebar.layers
  (:require
   [cuerdas.core :as str]
   [goog.events :as events]
   [lentes.core :as l]
   [potok.core :as ptk]
   [rumext.alpha :as mf]
   [rumext.core :as mx]
   [uxbox.builtins.icons :as i]
   [uxbox.main.data.shapes :as uds]
   [uxbox.main.data.workspace :as udw]
   [uxbox.main.refs :as refs]
   [uxbox.main.store :as st]
   [uxbox.main.ui.keyboard :as kbd]
   [uxbox.main.ui.shapes.icon :as icon]
   [uxbox.util.data :refer (read-string classnames)]
   [uxbox.util.dom :as dom]
   [uxbox.util.dom.dnd :as dnd]
   [uxbox.util.timers :as tm]
   [uxbox.util.router :as r])
  (:import goog.events.EventType))

;; --- Helpers

(defn- select-shape
  [selected item event]
  (dom/prevent-default event)
  (let [id (:id item)]
    (cond
      (or (:blocked item)
          (:hidden item))
      nil

      (.-ctrlKey event)
      (st/emit! (udw/select-shape id))

      (> (count selected) 1)
      (st/emit! (udw/deselect-all)
                (udw/select-shape id))

      (contains? selected id)
      (st/emit! (udw/select-shape id))

      :else
      (st/emit! (udw/deselect-all)
                (udw/select-shape id)))))

(defn- toggle-visibility
  [selected item event]
  (dom/stop-propagation event)
  (let [id (:id item)
        hidden? (:hidden item)]
    (if hidden?
      (st/emit! (uds/show-shape id))
      (st/emit! (uds/hide-shape id)))
    (when (contains? selected id)
      (st/emit! (udw/select-shape id)))))

(defn- toggle-blocking
  [item event]
  (dom/stop-propagation event)
  (let [id (:id item)
        blocked? (:blocked item)]
    (if blocked?
      (st/emit! (uds/unblock-shape id))
      (st/emit! (uds/block-shape id)))))

(defn- element-icon
  [item]
  (case (:type item)
    :icon (icon/icon-svg item)
    :image i/image
    :line i/line
    :circle i/circle
    :path i/curve
    :rect i/box
    :text i/text
    :group i/folder
    nil))

;; --- Shape Name (Component)

(mf/defc layer-name
  [{:keys [shape] :as props}]
  (let [local (mf/use-state {})
        on-blur (fn [event]
                  (let [target (dom/event->target event)
                        parent (.-parentNode target)
                        name (dom/get-value target)]
                    (set! (.-draggable parent) true)
                    (st/emit! (uds/rename-shape (:id shape) name))
                    (swap! local assoc :edition false)))
        on-key-down (fn [event]
                      (js/console.log event)
                      (when (kbd/enter? event)
                        (on-blur event)))
        on-click (fn [event]
                   (dom/prevent-default event)
                   (let [parent (.-parentNode (.-target event))]
                     (set! (.-draggable parent) false))
                   (swap! local assoc :edition true))]
    (if (:edition @local)
      [:input.element-name
       {:type "text"
        :on-blur on-blur
        :on-key-down on-key-down
        :auto-focus true
        :default-value (:name shape "")}]
      [:span.element-name
       {:on-double-click on-click}
       (:name shape "")])))

;; --- Layer Simple (Component)

(mf/defc layer-item
  [{:keys [shape selected] :as props}]
  (let [local (mf/use-state {})
        selected? (contains? selected (:id shape))
        select #(select-shape selected shape %)
        toggle-visibility #(toggle-visibility selected shape %)
        toggle-blocking #(toggle-blocking shape %)
        li-classes (classnames
                    :selected selected?
                    :hide (:dragging @local))
        body-classes (classnames
                      :selected selected?
                      :drag-active (:dragging @local)
                      :drag-top (= :top (:over @local))
                      :drag-bottom (= :bottom (:over @local))
                      :drag-inside (= :middle (:over @local)))]
    ;; TODO: consider using http://react-dnd.github.io/react-dnd/docs/overview
    (letfn [(on-drag-start [event]
              (let [target (dom/event->target event)]
                (dnd/set-allowed-effect! event "move")
                (dnd/set-data! event (:id shape))
                (dnd/set-image! event target 50 10)
                (tm/schedule #(swap! local assoc :dragging true))))
            (on-drag-end [event]
              (swap! local assoc :dragging false :over nil))
            (on-drop [event]
              (dom/stop-propagation event)
              (let [id (dnd/get-data event)
                    over (:over @local)]
                (case (:over @local)
                  :top (st/emit! (uds/drop-shape id (:id shape) :before))
                  :bottom (st/emit! (uds/drop-shape id (:id shape) :after)))
                (swap! local assoc :dragging false :over nil)))
            (on-drag-over [event]
              (dom/prevent-default event)
              (dnd/set-drop-effect! event "move")
              (let [over (dnd/get-hover-position event false)]
                (swap! local assoc :over over)))
            (on-drag-enter [event]
              (swap! local assoc :over true))
            (on-drag-leave [event]
              (swap! local assoc :over false))]
      [:li {:class li-classes}
       [:div.element-list-body
        {:class body-classes
         :style {:opacity (if (:dragging @local)
                            "0.5"
                            "1")}
         :on-click select
         :on-double-click #(dom/stop-propagation %)
         :on-drag-start on-drag-start
         :on-drag-enter on-drag-enter
         :on-drag-leave on-drag-leave
         :on-drag-over on-drag-over
         :on-drag-end on-drag-end
         :on-drop on-drop
         :draggable true}

        [:div.element-actions
         [:div.toggle-element
          {:class (when-not (:hidden shape) "selected")
           :on-click toggle-visibility}
          i/eye]
         [:div.block-element
          {:class (when (:blocked shape) "selected")
           :on-click toggle-blocking}
          i/lock]]
        [:div.element-icon (element-icon shape)]
        [:& layer-name {:shape shape}]]])))

;; --- Layer Group (Component)

;; --- Layers Tools (Buttons Component)

;; (defn- allow-grouping?
;;   "Check if the current situation allows grouping
;;   of the currently selected shapes."
;;   [selected shapes-map]
;;   (let [xform (comp (map shapes-map)
;;                     (map :group))
;;         groups (into #{} xform selected)]
;;     (= 1 (count groups))))

;; (defn- allow-ungrouping?
;;   "Check if the current situation allows ungrouping
;;   of the currently selected shapes."
;;   [selected shapes-map]
;;   (let [shapes (into #{} (map shapes-map) selected)
;;         groups (into #{} (map :group) shapes)]
;;     (or (and (= 1 (count shapes))
;;              (= :group (:type (first shapes))))
;;         (and (= 1 (count groups))
;;              (not (nil? (first groups)))))))

(mf/defc layers-tools
  "Layers widget options buttons."
  [{:keys [selected shapes] :as props}]
  #_(let [duplicate #(st/emit! (uds/duplicate-selected))
        group #(st/emit! (uds/group-selected))
        ungroup #(st/emit! (uds/ungroup-selected))
        delete #(st/emit! (udw/delete-selected))

        ;; allow-grouping? (allow-grouping? selected shapes)
        ;; allow-ungrouping? (allow-ungrouping? selected shapes)
        ;; NOTE: the grouping functionallity will be removed/replaced
        ;; with elements.
        allow-ungrouping? false
        allow-grouping? false
        allow-duplicate? (= 1 (count selected))
        allow-deletion? (pos? (count selected))]
    [:div.layers-tools
     [:ul.layers-tools-content
      [:li.clone-layer.tooltip.tooltip-top
       {:alt "Duplicate"
        :class (when-not allow-duplicate? "disable")
        :on-click duplicate}
       i/copy]
      [:li.group-layer.tooltip.tooltip-top
       {:alt "Group"
        :class (when-not allow-grouping? "disable")
        :on-click group}
       i/folder]
      [:li.degroup-layer.tooltip.tooltip-top
       {:alt "Ungroup"
        :class (when-not allow-ungrouping? "disable")
        :on-click ungroup}
       i/ungroup]
      [:li.delete-layer.tooltip.tooltip-top
       {:alt "Delete"
        :class (when-not allow-deletion? "disable")
        :on-click delete}
       i/trash]]]))

;; --- Layers Toolbox (Component)

(def ^:private shapes-iref
  (-> (l/key :shapes)
      (l/derive st/state)))

(mf/defc layers-toolbox
  [{:keys [page selected] :as props}]
  (let [shapes (mf/deref shapes-iref)
        on-click #(st/emit! (udw/toggle-flag :layers))]
    [:div#layers.tool-window
     [:div.tool-window-bar
      [:div.tool-window-icon i/layers]
      [:span "Layers"]
      [:div.tool-window-close {:on-click on-click} i/close]]
     [:div.tool-window-content
      [:ul.element-list
       (for [id (:shapes page)]
         [:& layer-item {:shape (get shapes id)
                         :selected selected
                         :key id}])]]
     [:& layers-tools {:selected selected
                       :shapes shapes}]]))

