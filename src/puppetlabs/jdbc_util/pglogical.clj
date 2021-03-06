(ns puppetlabs.jdbc-util.pglogical
  (:import [org.postgresql.util PSQLException PSQLState])
  (:require [clojure.java.jdbc :as jdbc]
            [puppetlabs.jdbc-util.core :refer [has-extension?]]))

(defn has-pglogical-extension? [db]
  (has-extension? db "pglogical"))

(defn- unsafe-escape-sql-quotes
  "Escape the given string so it can be \"safely\" passed as a string parameter
  to an sql query."
  [s]
  (clojure.string/replace s "'" "''"))

(defn wrap-ddl-for-pglogical
  "Wrap the given sql (presumably DDL) in a call to
  pglogical.replicate_ddl_command, escaping quotes and wrapping the statement so
  it won't return anything."
  [sql schema]
  (str "do 'begin perform "
       (unsafe-escape-sql-quotes
        (str "pglogical.replicate_ddl_command('"
             "set local search_path to " schema "; "
             (unsafe-escape-sql-quotes sql)
             "');"))
       " end;';"))

(defn update-pglogical-replication-set
  "Tries to update the default pglogical replication set to replicate all tables
  in the given schema. If the db user don't have rights to update pglogical,
  catches the exception and returns false. Other exceptions are passed through.
  Returns true if the replication set was successfully updated."
  [db schema]
  (try
    (jdbc/query db
                (str "select pglogical.replication_set_add_all_tables("
                     "'default',"
                     "'{\"" schema "\"}',"
                     "true"
                     ");"))
    true
    (catch PSQLException e
      (if (= "42501" (.getSQLState e))
        false
        (throw e)))))


