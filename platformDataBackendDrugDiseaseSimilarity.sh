#!/bin/bash

#/home/mkarmona
#/home/mkarmona/tmp/ot/drug-disease-similarity/19.09_drug-data.json
#/home/mkarmona/tmp/ot/drug-disease-similarity/19.09_efo-data.json
#/home/mkarmona/tmp/ot/drug-disease-similarity/19.09_expression-data.json
#/home/mkarmona/tmp/ot/drug-disease-similarity/19.09_gene-data.json
#/home/mkarmona/tmp/ot/drug-disease-similarity/evidences_sampled.json
#/home/mkarmona/tmp/ot/drug-disease-similarity/out_190920_evidences_drug_aggregation_1909/
#/home/mkarmona/tmp/ot/drug-disease-similarity/predictions.190905.parquet/
#/home/mkarmona/tmp/ot/drug-disease-similarity/protein_pair_interactions.json
#/home/mkarmona/tmp/ot/drug-disease-similarity/significant_AEs_by_drug.json
#/home/mkarmona/tmp/ot/drug-disease-similarity/significant_AEs_by_target.json
#/home/mkarmona/tmp/ot/drug-disease-similarity/studies.parquet/

export JAVA_OPTS="-Xms1G -Xmx4G"
time amm platformDataBackendDrugDiseaseSimilarity.sc \
  --drugFilename "/home/mkarmona/tmp/ot/drug-disease-similarity/19.09_drug-data.json" \
  --targetFilename "/home/mkarmona/tmp/ot/drug-disease-similarity/19.09_gene-data.json" \
  --diseaseFilename "/home/mkarmona/tmp/ot/drug-disease-similarity/19.09_efo-data.json" \
  --evidenceFilename "/home/mkarmona/tmp/ot/drug-disease-similarity/19.06_evidence-data-100k.json" \
  --interactionsFilename "/home/mkarmona/tmp/ot/drug-disease-similarity/protein_pair_interactions.json" \
  --aggregatedDrugsFilename "/home/mkarmona/tmp/ot/drug-disease-similarity/out_190920_evidences_drug_aggregation_1909/part-*" \
  --studiesFilename "/home/mkarmona/tmp/ot/drug-disease-similarity/studies.parquet/" \
  --predictionsFilename "/home/mkarmona/tmp/ot/drug-disease-similarity/predictions.190905.parquet/" \
  --faersByDrugFilename "/home/mkarmona/tmp/ot/drug-disease-similarity/significant_AEs_by_drug.json" \
  --faersByTargetFilename "/home/mkarmona/tmp/ot/drug-disease-similarity/significant_AEs_by_target.json" \
  --expressionFilename "/home/mkarmona/tmp/ot/drug-disease-similarity/19.09_expression-data.json" \
  --outputPathPrefix "drug_disease_similarity"