#!/bin/bash
#cat "$1" | elasticsearch_loader --es-host "http://localhost:9200" --index-settings-file "index_settings.json" --bulk-size 5000 --index cancerbiomarker --type cancerbiomarker json --json-lines -

export INDEX_SETTINGS="index_settings.json"
export RELEASE=''
export INDEX_NAME="network"
export TYPE_FIELD="network"
export INPUT="../out/interactions"
#export INPUT="gs://ot-snapshots/etl/latest/cancerBiomarkers"
export ES="http://localhost:9200"

./load_jsons.sh
