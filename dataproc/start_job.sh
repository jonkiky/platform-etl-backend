#!/bin/bash

: ' 
This script assumes that the cluster is running and configured as per the `create_cluster.sh` script. 

All default steps in config will be executed in a non-deterministic order. 
'
config=config.conf
jartoexecute=gs://ot-snapshots/etl/jars/jar_no_spark.jar

gcloud dataproc jobs submit spark \
           --cluster=etl-cluster-preview \
           --project=open-targets-eu-dev \
           --region=europe-west1 \
           --files=$config \
           --properties=spark.executor.extraJavaOptions=-Dconfig.file=$config,spark.driver.extraJavaOptions=-Dconfig.file=$config\
           --jar=$jartoexecute -- associationOTF

