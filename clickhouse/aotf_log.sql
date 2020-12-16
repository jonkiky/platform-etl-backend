

-- cat part-00* | clickhouse-client -h localhost --query="insert into ot.associations_otf_log format JSONEachRow "
-- cat part-00* | elasticsearch_loader --es-host http://es7-20-06:9200 --index-settings-file=../index.json --index evidences_aotf --timeout 120 --with-retry --id-field row_id --delete  json --json-lines -
create database if not exists ot;
create table if not exists ot.associations_otf_log(
    sourceId String,
    row_id String,
    disease_id String,
    target_id String,
    disease_data Nullable(String),
    target_data Nullable(String),
    datasource_id String,
    datatype_id String,
    row_score Float64
) engine = Log;
