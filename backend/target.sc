import $file.common
import common._

import org.apache.spark.SparkConf
import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.sql.functions._
import org.apache.spark.sql.functions.col
import org.apache.spark.sql._
import org.apache.spark.sql.types._
import com.typesafe.config.Config

object TargetHelpers {
  implicit class AggregationHelpers(df: DataFrame)(implicit ss: SparkSession) {
    import Configuration._
    import ss.implicits._

    def getHallMarksInfo: DataFrame = {

      df.withColumn(
          "hallMarks",
          struct(
            when(
              size(col("hallMarksRoot.attributes")) > 0,
              expr(
                "transform(hallMarksRoot.attributes, hm -> named_struct('pmid',cast(hm.pmid AS LONG),'attribute_name', hm.attribute_name, 'description', hm.description))"
              )
            ).alias("attributes"),
            when(
              size(col("hallMarksRoot.cancer_hallmarks")) > 0,
              expr(
                "transform(hallMarksRoot.cancer_hallmarks, hm -> named_struct('pmid',cast(hm.pmid AS LONG),'description', hm.description,'label', hm.label,'promote', hm.promote,'suppress', hm.suppress))"
              )
            ).alias("cancer_hallmarks"),
            when(
              size(col("hallMarksRoot.function_summary")) > 0,
              expr(
                "transform(hallMarksRoot.function_summary, hm -> named_struct('pmid',cast(hm.pmid AS LONG),'description', hm.description))"
              )
            ).alias("function_summary")
          )
        )
        .drop("hallMarksRoot")
    }

    // Manipulate safety info. Pubmed as long and unspecified_interaction_effects should be null in case of empty array.
    def getSafetyInfo: DataFrame = {

      df.withColumn(
          "safetyTransf",
          struct(
            when(
              size(col("safetyRoot.adverse_effects")) > 0,
              expr(
                "transform(safetyRoot.adverse_effects, sft -> named_struct('inhibition_effects', sft.inhibition_effects, 'unspecified_interaction_effects', if(size(sft.unspecified_interaction_effects) > 0, sft.unspecified_interaction_effects, null), 'organs_systems_affected', sft.organs_systems_affected, 'activation_effects', sft.activation_effects, 'references', transform(sft.references, v -> named_struct('pmid',cast(v.pmid AS LONG),'ref_label', v.ref_label, 'ref_link', v.ref_link))))"
              )
            ).alias("adverse_effects"),
            when(
              size(col("safetyRoot.safety_risk_info")) > 0,
              expr(
                "transform(safetyRoot.safety_risk_info, sft -> named_struct('organs_systems_affected', sft.organs_systems_affected, 'safety_liability', sft.safety_liability, 'references', transform(sft.references, v -> named_struct('pmid',cast(v.pmid AS LONG),'ref_label', v.ref_label, 'ref_link', v.ref_link))))"
              )
            ).alias("safety_risk_info")
          )
        )
        .withColumn(
          "safety",
          when(
            size(col("safetyTransf.adverse_effects")) > 0 and size(
              col("safetyTransf.safety_risk_info")
            ) < 1,
            struct(col("safetyTransf.adverse_effects"), lit(null).alias("safety_risk_info"))
          ).when(
              size(col("safetyTransf.adverse_effects")) < 1 and size(
                col("safetyTransf.safety_risk_info")
              ) > 0,
              struct(lit(null).alias("adverse_effects"), col("safetyTransf.safety_risk_info"))
            )
            .when(
              size(col("safetyTransf.adverse_effects")) > 0 and size(
                col("safetyTransf.safety_risk_info")
              ) > 0,
              col("safetyTransf")
            )
            .otherwise(lit(null))
        )
        .drop("safetyTransf", "safetyRoot")

    }

    def setIdAndSelectFromTargets: DataFrame = {
      val selectExpressions = Seq(
        "id",
        "approved_name as approvedName",
        "approved_symbol as approvedSymbol",
        "biotype as bioType",
        "case when (hgnc_id = '') then null else hgnc_id end as hgncId",
        "hallmarks as hallMarksRoot",
        "tractability as tractabilityRoot",
        "safety as safetyRoot",
        "chemicalprobes as chemicalProbes",
        "ortholog",
        "go as goRoot",
        "reactome",
        "name_synonyms as nameSynonyms",
        "symbol_synonyms as symbolSynonyms",
        "struct(chromosome, gene_start as start, gene_end as end, strand) as genomicLocation"
      )

      val uniprotStructure =
        """
          |case
          |  when (uniprot_id = '' or uniprot_id = null) then null
          |  else struct(uniprot_id as id,
          |    uniprot_accessions as accessions,
          |    uniprot_function as functions)
          |end as proteinAnnotations
          |""".stripMargin

      val dfTractabilityInfo = df
        .selectExpr(selectExpressions :+ uniprotStructure: _*)
        .withColumn(
          "tractability",
          when(
            size(col("tractabilityRoot.antibody.buckets")) > 0 and size(
              col("tractabilityRoot.smallmolecule.buckets")
            ) < 1,
            struct(col("tractabilityRoot.antibody"), lit(null).alias("smallmolecule"))
          ).when(
              size(col("tractabilityRoot.antibody.buckets")) < 1 and size(
                col("tractabilityRoot.smallmolecule.buckets")
              ) > 0,
              struct(lit(null).alias("antibody"), col("tractabilityRoot.smallmolecule"))
            )
            .when(
              size(col("tractabilityRoot.antibody.buckets")) > 0 and size(
                col("tractabilityRoot.smallmolecule.buckets")
              ) > 0,
              col("tractabilityRoot")
            )
            .otherwise(lit(null))
        )
        .drop("tractabilityRoot")

      val dfGoFixed = dfTractabilityInfo
        .withColumn(
          "goTransf",
          when(
            size(col("goRoot")) > 0,
            expr(
              "transform(goRoot, goEntry -> named_struct('id',goEntry.id, 'value_evidence', replace(goEntry.value.evidence,':','_'), 'value_project', goEntry.value.project, 'value_term', goEntry.value.term))"
            )
          )
        )
        .withColumn(
          "go",
          expr(
            "transform(goTransf, goItem -> named_struct('id',goItem.id, 'value', named_struct('evidence', goItem.value_evidence,'project', goItem.value_project,'term', goItem.value_term)))"
          )
        )
        .drop("goRoot", "goTransf")

      val dfHallMarksInfo = dfGoFixed.getHallMarksInfo

      // Manipulate safety info. Pubmed as long and unspecified_interaction_effects should be null in case of empty array.
      val dfSafetyInfo = dfHallMarksInfo.getSafetyInfo

      dfSafetyInfo
    }
  }
}

// This is option/step target in the config file
object Target extends LazyLogging {
  def apply(config: Config)(implicit ss: SparkSession) = {
    import ss.implicits._
    import TargetHelpers._

    val common = Configuration.loadCommon(config)
    val mappedInputs = Map(
      "target" -> Map("format" -> common.inputs.target.format, "path" -> common.inputs.target.path)
    )

    val inputDataFrame = SparkSessionWrapper.loader(mappedInputs)

    // The gene index contains keys with spaces. This step creates a new Dataframe with the proper keys
    val targetDFnewSchema = SparkSessionWrapper.replaceSpacesSchema(inputDataFrame("target"))

    val targetDF = targetDFnewSchema.setIdAndSelectFromTargets

    SparkSessionWrapper.save(targetDF, common.output + "/targets")

  }
}
