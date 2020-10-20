package io.opentargets.etl.backend

import com.typesafe.scalalogging.LazyLogging
import io.opentargets.etl.backend.Configuration.OTConfig
import io.opentargets.etl.backend.HelpersTest.{getTestDf, sampleInputFunction, testStruct}
import io.opentargets.etl.backend.spark.Helpers
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.apache.spark.sql.types._
import org.apache.spark.sql.{Column, DataFrame, Row, SparkSession}
import org.scalatest.prop.TableDrivenPropertyChecks
import io.opentargets.etl.backend.spark.Helpers._

import scala.util.Random

object HelpersTest {

  // given
  val sampleInputFunction: String => String = _.toUpperCase

  lazy val testStruct: StructType =
    StructType(
      StructField("a", IntegerType, nullable = true) ::
        StructField("b", LongType, nullable = false) ::
        StructField("c", BooleanType, nullable = false) :: Nil)
  lazy val testData: Seq[Row] = Seq(Row(1, 1L, true), Row(2, 2L, false))

  def getTestDf(sparkSession: SparkSession): DataFrame =
    sparkSession.createDataFrame(sparkSession.sparkContext.parallelize(testData), testStruct)
}

class HelpersTest
    extends AnyFlatSpecLike
    with TableDrivenPropertyChecks
    with Matchers
    with LazyLogging
    with SparkSessionSetup {

  "generateDefaultIoOutputConfiguration" should "generate a valid configuration for each of its input files" in {
    // given
    val config: OTConfig = Configuration.config.right.get
    val inputFileNames = Seq("a", "b", "c")
    // when
    val results = Helpers.generateDefaultIoOutputConfiguration(inputFileNames: _*)(config)
    // then
    assert(results.keys.size == inputFileNames.size)
    assert(
      results.values.forall(ioResConf =>
        ioResConf.format == config.common.outputFormat &&
          inputFileNames.contains(ioResConf.path.split("/").last)))

  }

  "separated values files" should "only be processed when they have a header and separator specified" in {
    // given
    val input = IOResourceConfig("name", "csv")
    // when
    lazy val results = Helpers.loadFileToDF(input)(sparkSession)
    // then
    assertThrows[AssertionError](results)
  }

  they should "load correctly when header and separator as specified" in {
    // given
    val path: String = this.getClass.getResource("/drugbank_v.csv").getPath
    val input = IOResourceConfig("csv", path, Some("\\t"), Some(true))
    // when
    val results = Helpers.loadFileToDF(input)(sparkSession)
    // then
    assert( !results.isEmpty, "The provided dataframe should not be empty.")
  }

  "Rename columns" should "rename all columns using given function" in {

    // when
    val results: StructType = renameAllCols(testStruct, sampleInputFunction)
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
    val results = renameAllCols(structWithArray, sampleInputFunction)
    // then
    assert(
      results(3).dataType
        .asInstanceOf[ArrayType]
        .elementType
        .asInstanceOf[StructType]
        .fieldNames
        .forall(_.head.isUpper))
  }

  "nest" should "return dataframe with selected columns nested under new column" in {
    // given
    val potentialColumnNames = Table(
      "column names",
      "a", // shadow existing column name and case
      "d", // new name
      "A", // shadow existing column name, different case
      "_", // special characters
      "!",
      Random.alphanumeric.take(100).mkString
    ) // very long
    val testDf = getTestDf(sparkSession)
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

  "columnExpr" should "return the same number of columns as there are strings in allCols param" in {
    // given
    val partial = Set("a", "b", "c")
    val full = Set("a", "b", "c", "d", "e")
    // when
    val result: Set[Column] = columnExpr(partial, full)
    // then
    assert(result.size equals full.size)
  }

  it should "throw an assertion error when there is an entry in mycol not in allcols" in {
    // given
    val partial = Set("a", "b", "c", "m")
    val full = Set("a", "b", "c", "d", "e")
    // when / then
    assertThrows[AssertionError]{
      val result = columnExpr(partial, full)
    }
  }

  "unionDataframeDifferentSchema" should "'join' two DataFrames with different schema" in {
    ???
  }
  "snakeToLowerCamelSchema" should "rename all columns in a dataframe from snake case to lower camel case" in {
    ???
  }
  "replaceSpacesSchema" should "convert spaces in column names to underscores" in {
    ???
  }

}
