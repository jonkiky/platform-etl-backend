package io.opentargets.etl.backend

import org.apache.spark.SparkConf
import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.sql.functions._
import org.apache.spark.sql.functions.col
import org.apache.spark.sql._
import org.apache.spark.sql.types._
import com.typesafe.config.Config
import better.files._
import better.files.File._
import io.opentargets.etl.backend.spark.Helpers
import io.opentargets.etl.backend.spark.Helpers.IOResourceConfig

object DiseaseHelpers {
  implicit class AggregationHelpers(df: DataFrame)(implicit ss: SparkSession) {
    import Configuration._
    import ss.implicits._

    def setIdAndSelectFromDiseases: DataFrame = {

      val efosSummary = df
        .withColumn(
          "ancestors",
          array_except(
            array_distinct(flatten(col("path_codes"))),
            array(col("id"))
          )
        )
        .drop("paths", "private", "_private", "path")

      val descendants = efosSummary
        .where(size(col("ancestors")) > 0)
        .withColumn("ancestor", explode(concat(array(col("id")), col("ancestors"))))
        .groupBy("ancestor")
        .agg(collect_set(col("id")).as("descendants"))
        .withColumnRenamed("ancestor", "id")
        .withColumn("phenotypes", lit(Array.empty[String]))
        .withColumn(
          "descendants",
          array_except(
            col("descendants"),
            array(col("id"))
          )
        )

      val efos = efosSummary
        .join(descendants, Seq("id"), "left")

      val efosRenamed = efos
        .withColumnRenamed("label", "name")
        .withColumnRenamed("definition", "description")
        .withColumnRenamed("efo_synonyms", "synonyms")
        .withColumnRenamed("synonyms_details", "synonymsDetails")
        .withColumnRenamed("therapeutic_codes", "therapeuticAreas")
        .withColumnRenamed("obsolete_terms", "obsoleteTerms")
        .drop(
          "definition_alternatives",
          "path_codes",
          "isTherapeuticArea",
          "leaf",
          "path_labels",
          "therapeutic_labels",
          "sources"
        )

      efosRenamed

    }
  }
}

object Disease extends LazyLogging {
  def compute()(implicit context: ETLSessionContext): DataFrame = {
    implicit val ss = context.sparkSession
    import ss.implicits._
    import DiseaseHelpers._

    val common = context.configuration.common
    val mappedInputs = Map(
      "disease" -> IOResourceConfig(
        common.inputs.disease.format,
        common.inputs.disease.path
      )
    )
    val inputDataFrame = Helpers.readFrom(mappedInputs)

    val diseaseDF = inputDataFrame("disease").setIdAndSelectFromDiseases

    diseaseDF
  }

  def apply()(implicit context: ETLSessionContext) = {
    implicit val ss = context.sparkSession
    import ss.implicits._
    import DiseaseHelpers._

    val common = context.configuration.common

    logger.info("transform disease dataset")
    val diseaseDF = compute()

    logger.info(s"write to ${context.configuration.common.output}/disease")
    val outputConfs = Map(
      "disease" -> IOResourceConfig(
        context.configuration.common.outputFormat,
        s"${context.configuration.common.output}/disease"
      )
    )

    Helpers.writeTo(outputConfs, Map("disease" -> diseaseDF))

    val therapeticAreaList = diseaseDF
      .filter(col("ontology.isTherapeuticArea") === true)
      .select("id")

    therapeticAreaList
      .coalesce(1)
      .write
      .option("header", "false")
      .csv(common.output + "/diseasesStaticTherapeuticarea")

    val efoBasicInfoDF =
      diseaseDF.select("id", "name", "parents").withColumnRenamed("parents", "parentIds")

    efoBasicInfoDF
      .coalesce(1)
      .write
      .json(common.output + "/diseasesStaticEfos")
  }
}
