#!/bin/bash

export INDEX_SETTINGS="index_settings.json"
export INDEX_NAME="20.02_openfda_faers"
export TYPE_FIELD="faer"
export INPUT="../faers"
export ES="http://localhost:9200"
# export ID="id"

./load_jsons.sh
