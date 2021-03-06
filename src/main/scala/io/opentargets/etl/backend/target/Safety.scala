package io.opentargets.etl.backend.target

import com.typesafe.scalalogging.LazyLogging
import io.opentargets.etl.backend.spark.Helpers.unionDataframeDifferentSchema
import org.apache.spark.sql.functions.{
  array,
  col,
  collect_set,
  element_at,
  explode,
  lit,
  split,
  struct,
  trim,
  when
}
import org.apache.spark.sql.{DataFrame, Dataset, SparkSession}

case class TargetSafety(id: String, safetyLiabilities: Array[SafetyEvidence])

case class TargetSafetyAssay(assayDescription: String, assayFormat: String, assayType: String)

case class SafetyEvidence(event: String,
                          eventId: String,
                          effects: Array[(String, String)],
                          tissue: SafetyTissue,
                          datasource: String,
                          pmid: String,
                          url: String,
                          assays: Array[TargetSafetyAssay])
case class SafetyTissue(label: String, efoId: String, modelName: String)

object Safety extends LazyLogging {

  def apply(adverseEventsDF: DataFrame, safetyRiskDF: DataFrame, toxicityDF: DataFrame)(
      implicit sparkSession: SparkSession): Dataset[TargetSafety] = {
    import sparkSession.implicits._

    logger.info("Computing target safety information.")

    // transform raw data frames into desired format
    val aeDF: DataFrame = transformAdverseEvents(adverseEventsDF)
    val tsDF: DataFrame = transformTargetSafety(safetyRiskDF)
    val toxDF: DataFrame =
      transformToxicity(toxicityDF, adverseEventsDF.select(col("symptom") as "term", col("efoId")))

    // combine into single dataframe and group by Ensembl id.
    // The data is relatively sparse, so expect lots of nulls.
    val combinedDF = unionDataframeDifferentSchema(Seq(aeDF, tsDF, toxDF))
      .transform(groupByEvidence)

    combinedDF.as[TargetSafety]
  }

  private def transformAdverseEvents(df: DataFrame): DataFrame = {
    logger.debug("Transforming target safety adverse events data.")
    val aeDF = df
      .select(
        col("ensemblId") as "id",
        col("symptom") as "event",
        col("efoId") as "eventId",
        col("ref") as "datasource",
        col("pmid"),
        col("url"),
        struct(
          col("biologicalSystem") as "label",
          col("uberonCode") as "efoId",
          lit(null) as "modelName"
        ) as "tissue",
        split(col("effect"), "_") as "effects"
      )
      .withColumn(
        "effects",
        struct(element_at(col("effects"), 1) as "effectType",
               when(element_at(col("effects"), 2) =!= "general", element_at(col("effects"), 2))
                 .otherwise(null) as "effectDose")
      )

    val effectsDF = aeDF
      .groupBy("id", "event", "datasource")
      .agg(collect_set(col("effects")) as "effects")

    aeDF.drop("effects").join(effectsDF, Seq("id", "event", "datasource"), "left_outer")
  }

  private def transformTargetSafety(df: DataFrame): DataFrame = {
    logger.debug("Transforming target safety safety risk data.")
    df.select(
      col("ensemblId") as "id",
      struct(
        col("biologicalSystem") as "label",
        col("uberonId") as "efoId",
        lit(null) as "modelName"
      ) as "tissue",
      col("ref") as "datasource",
      col("pmid"),
      col("url")
    )
  }

  private def transformToxicity(toxicityDF: DataFrame, uberonDF: DataFrame): DataFrame = {
    logger.debug("Transforming target safety toxicity data.")
    val etBaseDF = toxicityDF.select(
      col("ensembl_gene_id") as "id",
      struct(
        col("assay_description") as "assayDescription",
        col("assay_format") as "assayFormat",
        col("assay_format_type") as "assayType"
      ) as "assay",
      col("tissue") as "label", // map to uberon term to add tissue.efoId
      col("cell_short_name") as "modelName",
      col("data_source") as "datasource",
      col("data_source_reference_link") as "url"
    )

    val colsToGroupBy = etBaseDF.columns.filterNot(_ == "assay")
    val etWithAssayArray = etBaseDF
      .groupBy(colsToGroupBy.head, colsToGroupBy.tail: _*)
      .agg(collect_set("assay") as "assays")
    // add in efos
    val etWithEfo = etWithAssayArray
      .select(
        col("id"),
        split(col("label"), ",").as("label")
      )
      .select(col("id"), explode(col("label")).as("label"))
      .withColumn("label", trim(col("label")))
      .join(
        uberonDF,
        col("term") === col("label"),
        "left_outer"
      )
      .drop("term")
      .distinct

    etWithAssayArray
      .drop("label")
      .join(etWithEfo, Seq("id"), "left_outer")
      .distinct
      .withColumn("tissue", struct(col("label"), col("efoId"), col("modelName")))
      .drop("label", "efoId", "modelName")
  }

  private def groupByEvidence(df: DataFrame): DataFrame = {
    logger.debug("Grouping target safety by ensembl id.")
    df.select(
        col("id"),
        struct(
          col("event"),
          col("eventId"),
          col("effects"),
          col("tissue"),
          col("datasource"),
          col("pmid"),
          col("url"),
          col("assays")
        ) as "safety"
      )
      .groupBy("id")
      .agg(collect_set("safety") as "safetyLiabilities")
  }
}
