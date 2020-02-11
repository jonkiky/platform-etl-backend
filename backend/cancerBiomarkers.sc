import $file.common
import common._

import org.apache.spark.SparkConf
import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.sql.functions._
import org.apache.spark.sql.functions.col
import org.apache.spark.sql._
import org.apache.spark.sql.types._
import com.typesafe.config.Config

object CancerBiomarkersHelpers {
  implicit class AggregationHelpers(df: DataFrame)(implicit ss: SparkSession) {
    import Configuration._
    import ss.implicits._

    def getBiomarkerTargetDiseaseDrugEntity: DataFrame = {

      val selectExpressions = Seq(
        "id",
        "cancerbiomarkers as cancerBiomarkers"
      )

      val dfBiomarkersInput = df.selectExpr(selectExpressions: _*).withColumnRenamed("id", "target")

      val dfExtractInfo = (dfBiomarkersInput
        .withColumn(
          "cancerBiomarkersDetails",
          when(
            size(col("cancerBiomarkers")) > 0,
            expr(
              "transform(cancerBiomarkers, bioRow -> named_struct('individualbiomarker',bioRow.individualbiomarker,'biomarkerId', bioRow.biomarker,'diseases', bioRow.diseases,'drugName',bioRow.drugfullname,'associationType',bioRow.association, 'evidenceLevel', bioRow.evidencelevel,'sources_other', bioRow.references.other, 'sources_pubmed', bioRow.references.pubmed))"
            )
          )
        )
        .withColumn("details", explode(col("cancerBiomarkersDetails")))
        .drop("cancerBiomarkersDetails"))
        .groupBy(
          col("details.individualbiomarker"),
          col("details.biomarkerId"),
          col("details.drugName"),
          col("details.associationType"),
          col("details.evidenceLevel"),
          col("details.sources_pubmed"),
          col("details.sources_other"),
          col("target")
        )
        .agg(collect_list("details.diseases").as("diseasesNested"))
        .withColumn("diseases", flatten(col("diseasesNested")))
        .drop("diseasesNested")
        .withColumn("disease", explode(col("diseases.id")))

      /** The field individualbiomarker contains a specific fields if the biomarker id is a composed id.
		  It is important to idenfity the unique identifier id.
		  Below the id is the proper identifier
		**/
      val biomarkerIdentifier =
        """
          |case
          |  when (individualbiomarker = '' or individualbiomarker = null) then biomarkerId
          |  else individualbiomarker
          |end as id
          |""".stripMargin

      val selectExpressionBiomarkers =
        Seq(
          "individualbiomarker",
          "biomarkerId",
          "drugName",
          "target",
          "disease",
          "evidenceLevel",
          "associationType",
          "sources_pubmed",
          "sources_other"
        )

      val dfBiomarkers =
        dfExtractInfo
          .selectExpr(selectExpressionBiomarkers :+ biomarkerIdentifier: _*)
          .drop("biomarkerId", "individualbiomarker")

      dfBiomarkers

    }
  }
}

// This is option/step cancerbiomarkers in the config file
// GENE ID with a subset of cases.
object CancerBiomarkers extends LazyLogging {
  def apply(config: Config)(implicit ss: SparkSession) = {
    import ss.implicits._
    import CancerBiomarkersHelpers._

    val common = Configuration.loadCommon(config)
    val mappedInputs = Map("target" -> common.inputs.target)
    val inputDataFrame = SparkSessionWrapper.loader(mappedInputs)

    val cancerBiomakerDf = inputDataFrame("target").getBiomarkerTargetDiseaseDrugEntity
    SparkSessionWrapper.save(cancerBiomakerDf, common.output + "/cancerBiomarkers")
  }
}
