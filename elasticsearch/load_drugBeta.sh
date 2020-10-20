#cat "$1" | elasticsearch_loader --es-host "http://localhost:9200" --index-settings-file "index_settings.json" --bulk-size 5000 --index drugs --type drug --id-field id json --json-lines -

export INDEX_SETTINGS="index_settings.json"
export RELEASE=''
export INDEX_NAME="drug_beta"
export TYPE_FIELD="drug_beta"
export INPUT="../out/drugs"
#export INPUT="gs://ot-snapshots/etl/latest/drugs-beta"
export ES="http://localhost:9200"
export ID="id"

./load_jsons.sh