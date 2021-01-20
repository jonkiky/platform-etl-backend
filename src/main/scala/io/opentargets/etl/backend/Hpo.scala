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
import io.opentargets.etl.backend.spark.Helpers.unionDataframeDifferentSchema


object HpoHelpers {
  implicit class AggregationHelpers(df: DataFrame)(implicit ss: SparkSession) {
    import Configuration._
    import ss.implicits._

    def getMondo(diseaseXRefs: DataFrame): DataFrame = {

      val mondoBase = df
        .where(size(col("phenotypes")) > 0)
        .withColumn("phenotypeId", explode(col("phenotypes")))
        .filter(col("phenotypeId").startsWith("HP"))
        .filter(col("id").startsWith(lit("MONDO")))
        .withColumn("newId", regexp_replace(col("id"), "_", ":"))
        .withColumnRenamed("name", "mondoName")
        .withColumn("qualifierNot", lit(false))


      val mondoDiseaseMapping = mondoBase.join(diseaseXRefs, col("dbXRefId") === col("newId"))
       .selectExpr(
        "phenotypeId as phenotype",
        "newId as diseaseFromSourceId",
        "mondoName as diseaseFromSource",
        "name as diseaseName",
        "disease",
        "qualifierNot",
         "resource"
      ).distinct

      mondoDiseaseMapping

    }

    def getHpo: DataFrame = {
      df.filter(size(col("namespace")) > 0)
    }

    def getDiseaseHpo(diseaseXRefs: DataFrame): DataFrame = {

      val orphaDiseaseXRefs = diseaseXRefs.select("disease","name").filter(col("id").startsWith("Orphanet"))
        .withColumn("dbXRefId", regexp_replace(col("disease"), "Orphanet_", "ORPHA:"))

      val xRefs = diseaseXRefs.unionByName(orphaDiseaseXRefs)

      val hpoDiseaseMapping =
        xRefs.join(df, col("dbXRefId") === col("databaseId"))
          .withColumn("qualifierNOT", when(col("qualifier").isNull, false).otherwise(true))
          .distinct
          .withColumn("phenotypeId", regexp_replace(col("HPOId"), ":", "_"))
          .selectExpr(
            "phenotypeId as phenotype",
            "aspect",
            "biocuration as bioCuration",
            "databaseId as diseaseFromSourceId",
            "diseaseName as diseaseFromSource",
            "name as diseaseName",
            "evidenceType",
            "frequency",
            "modifiers",
            "onset",
            "qualifier",
            "qualifierNot",
            "references",
            "sex",
            "disease",
            "resource"
          )

      //val reverse = hpoDiseaseMapping.join(df, col("id") ===  col("phenotypeId"), "left")

      //reverse.write.json("reverse")

      hpoDiseaseMapping

    }
    def createEvidence: DataFrame = {

      df.groupBy("disease", "phenotype")
      .agg(collect_list(
        struct(
          $"aspect",
          $"bioCuration",
          $"diseaseFromSourceId",
          $"diseaseFromSource",
          $"diseaseName",
          $"evidenceType",
          $"frequency",
          $"modifiers",
          $"onset",
          $"qualifier",
          $"qualifierNot",
          $"references",
          $"sex",
          $"resource"
          )
        ).as("evidence"))

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
      ),
      "mondo" -> IOResourceConfig(
        common.inputs.mondo.format,
        common.inputs.mondo.path
      )
    )

    val inputDataFrame = Helpers.readFrom(mappedInputs)
    val diseaseXRefs = disease
      .withColumn("dbXRefId", explode(col("dbXRefs")))
      .withColumnRenamed("id", "disease")
      .select("dbXRefId", "disease", "name")
    val mondo = inputDataFrame("mondo").getMondo(diseaseXRefs)
    val diseasehpo = inputDataFrame("diseasehpo").getDiseaseHpo(diseaseXRefs)
    val unionDiseaseHpo = unionDataframeDifferentSchema(diseasehpo, mondo).createEvidence

    val hpo = inputDataFrame("hpo").getHpo

    Map(
      "diseasehpo" -> unionDiseaseHpo,
      "hpo" -> hpo
    )
  }

  def apply()(implicit context: ETLSessionContext) = {
    implicit val ss = context.sparkSession
    import ss.implicits._

    val diseases = Disease.compute().selectExpr("id as disease", "name", "dbXRefs")
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
