import Dependencies._

val buildResolvers = Seq(
  "Typesafe Repo" at "https://repo.typesafe.com/typesafe/releases/",
  "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
  "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases",
  "Bintray Repo" at "https://dl.bintray.com/spark-packages/maven/"
)

lazy val root = (project in file("."))
  .settings(
    inThisBuild(
      List(
        organization := "io.opentargets",
        scalaVersion := "2.12.12"
      )
    ),
    name := "io-opentargets-etl-backend",
    version := "0.5.0",
    resolvers ++= buildResolvers,
    libraryDependencies ++= sparkDeps,
    libraryDependencies ++= graphDeps,
    libraryDependencies ++= aoyi,
    libraryDependencies += betterFiles,
    libraryDependencies ++= loggingDeps,
    libraryDependencies += typeSafeConfig,
    libraryDependencies ++= configDeps,
    libraryDependencies ++= testingDeps,
    libraryDependencies ++= cats,
    libraryDependencies ++= monocle,
    libraryDependencies ++= smile,
    libraryDependencies ++= johnS,
    testFrameworks += new TestFramework("minitest.runner.Framework"),
    assemblyMergeStrategy in assembly := {
      case PathList("META-INF", "services", "org.apache.hadoop.fs.FileSystem") =>
        MergeStrategy.filterDistinctLines
      case PathList("META-INF", "services", "org.apache.spark.sql.sources.DataSourceRegister") =>
        MergeStrategy.concat
      case PathList("META-INF", xs @ _*) => MergeStrategy.discard
      case _                             => MergeStrategy.first
    }
  )
