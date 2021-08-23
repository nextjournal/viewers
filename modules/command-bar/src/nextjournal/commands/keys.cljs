(ns nextjournal.commands.keys
  (:require ["w3c-keyname" :as keyname]
            [applied-science.js-interop :as j]
            [clojure.string :as str]
            [goog.labs.userAgent.platform :as platform]
            [nextjournal.log :as log]))

(def platform (if (platform/isMacintosh) :mac :pc))

(defn modifier [keyname]
  (case keyname
    "Shift" :Shift
    ("Ctrl" "Control") :Control
    ("Alt" "Option") :Alt
    ("Command" "Super" "Meta" "OS") :Meta
    ;; default modifier for OS X is cmd and for others is ctrl
    "Mod" (if (keyword-identical? platform :mac)
            :Meta
            :Control)
    nil))

(def printables
  {"Shift" "⇧"
   "Control" "⌃"
   "Alt" (case platform :mac "⌥"
                        "Alt")
   "Meta" (case platform :mac "⌘"
                         "Meta")
   "ArrowDown" "↓"
   "ArrowUp" "↑"
   "ArrowLeft" "←"
   "ArrowRight" "→"
   "Enter" "⏎"
   "Backspace" "⌫"})

(defn chord->path [chord]
  ;; a chord is a map, eg {:Alt true ...}
  (let [ordered-mods (->> [:Alt :Control :Shift :Meta]
                          (reduce (fn [out mod]
                                    (cond-> out (mod chord) (conj (name mod)))) []))]
    (cond-> ordered-mods
            (not (modifier (:key chord)))
            (conj (:key chord)))))

(defn friendly-path
  "Human-readable representation of a path"
  [path]
  (->> (mapv #(or (printables %) %) path)
       (str/join " ")))

;; Data

(defn normalize-keyname [keyname]
  (cond-> keyname
          (= 1 (count keyname))
          (str/upper-case)))

;; Behavior

(defn string->chord [^string string]
  (->> (str/split string #"[ -]")
       (reduce (fn [m ^string keyname]
                 (if-let [mod (modifier keyname)]
                   (assoc m mod true)
                   (do
                     (assert (not (:key m))
                             (str "More than one non-modifier key bound in " string))
                     (assoc m :key (normalize-keyname keyname))))) {})))

(defn e->chord [e]
  (->> {:Shift ["Shift"]
        :Control ["Control"]
        :Alt ["Alt" "AltGraph"]
        :Meta ["Meta" "OS" "Win"]}
       (reduce-kv (fn [m key attrs]
                    (cond-> m
                            (some #(j/call e :getModifierState %) attrs)
                            (assoc key true)))
                  {:key (normalize-keyname (keyname/keyName e))})))


(defn- resolve-keys [keys]
  (cond (string? keys) [keys]
        (map? keys) [(platform keys (:default keys))]
        (vector? keys) (vec (mapcat resolve-keys keys))))

(defn parse-keys [keys]
  (let [keys (resolve-keys keys)
        paths (mapv (comp chord->path string->chord) keys)
        printable (friendly-path (first paths))]
    (log/trace :printable printable :paths paths :keys keys)
    #:keys{:resolved keys
           :paths paths
           :printable printable}))