#!/bin/bash

#cat "$1" | elasticsearch_loader --es-host "http://localhost:9200" --index-settings-file "index_settings.json" --bulk-size 5000 --index diseases --type disease --id-field id json --json-lines -
export INDEX_SETTINGS="index_settings.json"
export RELEASE=''
export INDEX_NAME="disease_hpo"
export TYPE_FIELD="disease_hpo"
export INPUT="../out/diseasehpo"
export ES="http://localhost:9200"
#export ID="id"

./load_jsons.sh
