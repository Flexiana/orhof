(ns build
  "Builds the orhof jar and deploys it to Clojars.

     clojure -T:build jar       target/orhof-<version>.jar
     clojure -T:build install   into the local Maven repository
     clojure -T:build deploy    to Clojars; needs CLOJARS_USERNAME and
                                CLOJARS_PASSWORD (a deploy token)

   The version comes from the ORHOF_VERSION environment variable. The release
   workflow sets it from the git tag: tag v1.0.0 releases 1.0.0."
  (:require [clojure.tools.build.api :as b]
            [deps-deploy.deps-deploy :as dd]))

(def lib 'io.github.flexiana/orhof)
(def version (or (System/getenv "ORHOF_VERSION") "1.0.0-SNAPSHOT"))
(def class-dir "target/classes")
(def jar-file (format "target/%s-%s.jar" (name lib) version))

(defn clean [_]
  (b/delete {:path "target"}))

(defn jar [_]
  (clean nil)
  (b/write-pom {:class-dir class-dir
                :lib       lib
                :version   version
                :basis     (b/create-basis {:project "deps.edn"})
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
  (dd/deploy {:installer :local
              :artifact  (b/resolve-path jar-file)
              :pom-file  (b/pom-path {:lib lib :class-dir class-dir})}))

(defn deploy [_]
  (jar nil)
  (dd/deploy {:installer :remote
              :artifact  (b/resolve-path jar-file)
              :pom-file  (b/pom-path {:lib lib :class-dir class-dir})}))
