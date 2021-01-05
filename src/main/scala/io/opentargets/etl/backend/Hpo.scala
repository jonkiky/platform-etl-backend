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
import io.opentargets.etl.backend.Disease.{compute, logger}
import io.opentargets.etl.backend.spark.Helpers
import io.opentargets.etl.backend.spark.Helpers.IOResourceConfig

object HpoHelpers {
  implicit class AggregationHelpers(df: DataFrame)(implicit ss: SparkSession) {
    import Configuration._
    import ss.implicits._


    def getHpo: DataFrame = {
      df.filter(size(col("namespace")) > 0)
    }

    def getDiseaseHpo(disease: DataFrame): DataFrame = {

      val diseaseXRefs = disease
        .withColumn("dbXRefId", explode(col("dbXRefs")))
        .withColumnRenamed("id", "diseaseId")
        .select("dbXRefId", "diseaseId", "name")

      val hpoDiseaseMapping =
        df.join(diseaseXRefs, col("dbXRefId") === col("databaseId"))
          .withColumn("qualifierNOT", when(col("qualifier").isNull, false).otherwise(true))
          .distinct
          .withColumn("phenotypeId", regexp_replace(col("HPOId"), ":", "_"))
          .selectExpr(
            "phenotypeId",
            "aspect",
            "biocuration as bioCuration",
            "databaseId as diseaseFromSourceId",
            "diseaseName as diseaseFromSource",
            "name as diseaseName",
            "evidence",
            "frequency",
            "modifier",
            "onset",
            "qualifier",
            "qualifierNot",
            "reference as referenceId",
            "sex",
            "diseaseId"
          )

      //val reverse = hpoDiseaseMapping.join(df, col("id") ===  col("phenotypeId"), "left")

      //reverse.write.json("reverse")

      hpoDiseaseMapping

    }

  }
}

object Hpo extends LazyLogging {

  def compute(disease: DataFrame)(implicit context: ETLSessionContext): Map[String, DataFrame] = {
    implicit val ss = context.sparkSession
    import ss.implicits._
    import HpoHelpers._

    val common = context.configuration.common

    val mappedInputs = Map(
      "diseasehpo" -> IOResourceConfig(
        common.inputs.diseasehpo.format,
        common.inputs.diseasehpo.path
      ),
      "hpo" -> IOResourceConfig(
        common.inputs.hpo.format,
        common.inputs.hpo.path
      )
    )

    val inputDataFrame = Helpers.readFrom(mappedInputs)
    val diseasehpo = inputDataFrame("diseasehpo").getDiseaseHpo(disease)

    val hpo = inputDataFrame("hpo").getHpo

    Map(
      "diseasehpo" -> diseasehpo,
      "hpo" -> hpo
    )
  }

  def apply()(implicit context: ETLSessionContext) = {
    implicit val ss = context.sparkSession
    import ss.implicits._

    val diseases = Disease.compute().selectExpr("id as diseaseId", "name", "dbXRefs")
    val hpoInfo = compute(diseases)

    logger.info(s"write to ${context.configuration.common.output}/hpo")

    val outputs = hpoInfo.keys map (name =>
      name -> Helpers.IOResourceConfig(
        context.configuration.common.outputFormat,
        context.configuration.common.output + s"/$name"
      )
    )

    Helpers.writeTo(outputs.toMap, hpoInfo)

  }
}
