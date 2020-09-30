package io.opentargets.etl.backend

import com.typesafe.scalalogging.LazyLogging
import io.opentargets.etl.backend.Configuration.OTConfig
import org.apache.spark.sql.types._
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import io.opentargets.etl.backend.SparkHelpers._
import org.apache.spark.sql.{DataFrame, Row}
import org.apache.spark.sql.functions.lower
import org.scalatest.prop.TableDrivenPropertyChecks
import pureconfig.ConfigReader

import scala.util.Random

class SparkHelpersTest
    extends AnyFlatSpecLike
    with TableDrivenPropertyChecks
    with Matchers
    with LazyLogging
    with SparkSessionSetup {
  // given
  val renameFun: String => String = _.toUpperCase
  lazy val testStruct: StructType =
    StructType(
      StructField("a", IntegerType, nullable = true) ::
        StructField("b", LongType, nullable = false) ::
        StructField("c", BooleanType, nullable = false) :: Nil)
  lazy val testData: Seq[Row] = Seq(Row(1, 1L, true), Row(2, 2L, false))
  lazy val testDf: DataFrame =
    sparkSession.createDataFrame(sparkSession.sparkContext.parallelize(testData), testStruct)

  "generateDefaultIoOutputConfiguration" should "generate a valid configuration for each of its input files" in {
    // given
    val config: OTConfig = Configuration.config.right.get
    val inputFileNames = Seq("a", "b", "c")
    // when
    val results = SparkHelpers.generateDefaultIoOutputConfiguration(inputFileNames: _*)(config)
    // then
    assert(results.keys.size == inputFileNames.size)
    assert(
      results.values.forall(ioResConf =>
        ioResConf.format == config.common.outputFormat &&
          inputFileNames.contains(ioResConf.path.split("/").last)))

  }

  "Rename columns" should "rename all columns using given function" in {

    // when
    val results: StructType = renameAllCols(testStruct, renameFun)
    // then
    assert(results.fields.forall(sf => sf.name.head.isUpper))
  }

  it should "correctly rename columns in nested arrays" in {
    // given
    val structWithArray = testStruct
      .add("d",
           ArrayType(
             new StructType()
               .add("e", StringType)
               .add("f", StringType)
               .add("g", IntegerType)))
    // when
    val results = renameAllCols(structWithArray, renameFun)
    // then
    assert(
      results(3).dataType
        .asInstanceOf[ArrayType]
        .elementType
        .asInstanceOf[StructType]
        .fieldNames
        .forall(_.head.isUpper))
  }

  "applyFunToColumn" should "apply the function to the column and return a dataframe with the same column names as in the input dataframe" in {
    import sparkSession.implicits._
    // given
    val df = Seq("UPPER").toDF("a")
    // when
    val results = df.transform(SparkHelpers.applyFunToColumn("a", _, lower))
    // then
    // column names are unchanged
    assert(results.columns sameElements df.columns)
    // function was applied to elements
    assert(results.head.getString(0).forall(c => c.isLower))
  }

  private val potentialColumnNames = Table(
    "column names",
    "a", // shadow existing column name and case
    "d", // new name
    "A", // shadow existing column name, different case
    "_", // special characters
    "!",
    Random.alphanumeric.take(100).mkString
  ) // very long
  "nest" should "return dataframe with selected columns nested under new column" in {
    // given
    logger.debug(s"Input DF structure ${testDf.printSchema}")
    val columnsToNest = testDf.columns.toList
    forAll(potentialColumnNames) { (colName: String) =>
      // when
      val results = nest(testDf, columnsToNest, colName)
      logger.debug(s"Output DF schema: ${results.printSchema}")
      // then
      assertResult(1, s"All columns should be listed under new column $colName") {
        results.columns.length
      }
      assertResult(colName, "The nesting column should be appropriately named.") {
        results.columns.head
      }
    }

  }

}
