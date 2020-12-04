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

    def getHpoInfo(disease: DataFrame): DataFrame = {

      val diseaseXRefs = disease
        .withColumn("dbXRefId", explode(col("dbXRefs")))
        .withColumnRenamed("id", "diseaseId")
        .select("dbXRefId", "diseaseId", "name")

      val hpoDiseaseMapping =
        df.join(diseaseXRefs, col("dbXRefId") === col("databaseId"), "left")
          .withColumn("qualifierNOT",   when(col("qualifier").isNull, false ).otherwise(true))
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

  def compute(disease: DataFrame)(implicit context: ETLSessionContext): DataFrame = {
    implicit val ss = context.sparkSession
    import ss.implicits._
    import HpoHelpers._

    val common = context.configuration.common
    val mappedInputs = Map(
      "hpo" -> IOResourceConfig(
        common.inputs.hpo.format,
        common.inputs.hpo.path
      )
    )
    val inputDataFrame = Helpers.readFrom(mappedInputs)

    val hpo = inputDataFrame("hpo").getHpoInfo(disease)

    hpo
  }

  def apply()(implicit context: ETLSessionContext) = {
    implicit val ss = context.sparkSession
    import ss.implicits._

    val diseases = Disease.compute().selectExpr("id as diseaseId", "name", "dbXRefs")
    val hpo = compute(diseases)

    logger.info(s"write to ${context.configuration.common.output}/hpo")
    val outputConfs = Map(
      "hpo" -> IOResourceConfig(
        context.configuration.common.outputFormat,
        s"${context.configuration.common.output}/hpo"
      )
    )

    Helpers.writeTo(outputConfs, Map("hpo" -> hpo))
  }
}
