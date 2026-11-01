#!/bin/sh
# Runs once, when the database volume is first created. Creates a database and a
# user that owns it but has no superuser rights.
set -e

psql -v ON_ERROR_STOP=1 --username postgres --dbname postgres \
    -v app_db="$APP_DB_NAME" -v app_user="$APP_DB_USER" -v app_password="$APP_DB_PASSWORD" <<'SQL'
CREATE ROLE :"app_user" LOGIN PASSWORD :'app_password' NOSUPERUSER NOCREATEDB NOCREATEROLE;
CREATE DATABASE :"app_db" OWNER :"app_user";
REVOKE ALL ON DATABASE :"app_db" FROM PUBLIC;
SQL
