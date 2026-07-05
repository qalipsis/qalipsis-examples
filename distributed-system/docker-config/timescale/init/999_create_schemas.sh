#!/bin/bash

# Script to create the initial schemas required to use QALIPSIS properly.
create_sql=`mktemp`

cat <<EOF >${create_sql}

CREATE SCHEMA IF NOT EXISTS events AUTHORIZATION ${POSTGRES_USER};
CREATE SCHEMA IF NOT EXISTS meters AUTHORIZATION ${POSTGRES_USER};
CREATE SCHEMA IF NOT EXISTS qalipsis AUTHORIZATION ${POSTGRES_USER};
CREATE SCHEMA IF NOT EXISTS qalipsis_liquibase AUTHORIZATION ${POSTGRES_USER};

EOF

if [ -z "${POSTGRESQL_PASSWORD:-}" ]; then
	POSTGRESQL_PASSWORD=${POSTGRES_PASSWORD:-}
fi
export PGPASSWORD="$POSTGRESQL_PASSWORD"

echo "Creating the required schemas for QALIPSIS"
psql -U "${POSTGRES_USER}" "${POSTGRES_DB}" -f ${create_sql}