(defproject cryptoarbitrage "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"] [clj-http "3.9.0"] [org.clojure/data.json "0.2.6"] [com.novemberain/monger "3.1.0"] [http-kit "2.2.0"] [compojure "1.6.0"] [clj-time "0.14.4"] [com.draines/postal "2.0.2"]]
  :plugins [[lein-gorilla "0.4.0" ]]
  :main ^:skip-aot cryptoarbitrage.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
