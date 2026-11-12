(ns build
  "Builds the orhof jar.

     clojure -T:build jar       target/orhof-<version>.jar and its pom.xml
     clojure -T:build install   into the local Maven repository

   The version comes from the ORHOF_VERSION environment variable. The release
   workflow sets it from the git tag (v1.0.0 builds 1.0.0) and then deploys
   with the :deploy alias, which `clojure -T:build paths` prints arguments for."
  (:require [clojure.tools.build.api :as b]))

(def lib 'com.flexiana/orhof)
(def version (or (System/getenv "ORHOF_VERSION") "1.0.0-SNAPSHOT"))
(def class-dir "target/classes")
(def jar-file (format "target/%s-%s.jar" (name lib) version))
(def basis (delay (b/create-basis {:project "deps.edn"})))

(defn clean [_]
  (b/delete {:path "target"}))

(defn jar [_]
  (clean nil)
  (b/write-pom {:class-dir class-dir
                :lib       lib
                :version   version
                :basis     @basis
                :src-dirs  ["src"]
                :scm       {:url                 "https://github.com/Flexiana/orhof"
                            :connection          "scm:git:https://github.com/Flexiana/orhof.git"
                            :developerConnection "scm:git:ssh://git@github.com/Flexiana/orhof.git"
                            :tag                 (str "v" version)}
                :pom-data  [[:description "Operations research algorithms as compositions of higher-order functions."]
                            [:url "https://github.com/Flexiana/orhof"]
                            [:licenses
                             [:license
                              [:name "MIT License"]
                              [:url "https://opensource.org/license/mit"]]]]})
  (b/copy-dir {:src-dirs ["src"] :target-dir class-dir})
  (b/jar {:class-dir class-dir :jar-file jar-file})
  (println "Built" jar-file))

(defn install [_]
  (jar nil)
  (b/install {:basis @basis :lib lib :version version :jar-file jar-file :class-dir class-dir}))

(defn paths
  "Prints the :artifact and :pom-file arguments for clojure -X:deploy."
  [_]
  (println (pr-str :artifact jar-file :pom-file (b/pom-path {:lib lib :class-dir class-dir}))))
