import $file.common
import common._

import org.apache.spark.SparkConf
import com.typesafe.scalalogging.LazyLogging
import org.apache.spark.sql.functions._
import org.apache.spark.sql.functions.col
import org.apache.spark.sql._
import org.apache.spark.sql.types._
import com.typesafe.config.Config

// This is option/step eco in the config file
object Eco extends LazyLogging {
  def apply(config: Config)(implicit ss: SparkSession) = {
    import ss.implicits._

    val common = Configuration.loadCommon(config)
    val mappedInputs = Map("eco" -> common.inputs.eco)
    val inputDataFrame = SparkSessionWrapper.loader(mappedInputs)
    val ecoDF = inputDataFrame("eco")

    SparkSessionWrapper.save(ecoDF, common.output + "/eco")

  }
}
