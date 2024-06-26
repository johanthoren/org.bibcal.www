(defproject org.bibcal/www "0.7.6-SNAPSHOT"
  :description "Public website at www.bibcal.org"
  :url "https://www.bibcal.org"
  :license {:name "ISC"
            :comment "ISC License"
            :url "https://choosealicense.com/licenses/isc"
            :year 2021
            :key "isc"}
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [compojure "1.7.0"]
                 [ring/ring-defaults "0.3.4"]
                 [xyz.thoren/luminary "0.7.2"]
                 [hiccup "1.0.5"]
                 [hiccup-table "0.2.0"]
                 [cheshire "5.11.0"]
                 [trptcolin/versioneer "0.2.0"]
                 [tick "0.6.2"]]
  :plugins [[lein-ring "0.12.5"]
            [lein-shell "0.5.0"]
            [lein-kibit "0.1.8"]]
  :ring {:handler org.bibcal.www/app}
  :profiles {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                                  [ring/ring-mock "0.3.2"]]}}
  :aliases
  {"clj-kondo"
   ["shell" "clj-kondo" "--lint" "src"]}
  :release-tasks [["clj-kondo"]
                  ;;["kibit"] ; Disabled until kibit allows disabling select rules.
                  ["test"]
                  ["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]])
