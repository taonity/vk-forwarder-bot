#!/bin/bash
# TODO make chmod +x

mkdir -p /backup/current

pg_dumpall -c -U ${POSTGRES_USER} > /backup/current/dump.sql