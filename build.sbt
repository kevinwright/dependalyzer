inThisBuild(
  Seq(
    organization := "com.example",
    scalaVersion := "3.3.0"
  )
)

val coursierVersion = "2.1.4"
val neo4jVersion = "5.9.0"

lazy val root = (project in file("."))
  .enablePlugins(FlywayPlugin)
  .settings(
    name := "dependency-fetcher",
    libraryDependencies ++= Seq(
      // Java
      "org.neo4j" % "neo4j" % neo4jVersion,
      "org.neo4j" % "neo4j-bolt" % neo4jVersion,
      "org.neo4j.driver" % "neo4j-java-driver" % neo4jVersion,
      "software.amazon.awssdk" % "s3" % "2.20.68",

      // Scala 2.13
      ("io.get-coursier" %% "coursier" % coursierVersion).cross(CrossVersion.for3Use2_13),

      // Scala 3
      "com.lihaoyi" %% "fansi" % "0.4.0",
      "com.lihaoyi" %% "pprint" % "0.8.1",
      "dev.zio" %% "zio" % "2.0.15",
      "dev.zio" %% "zio-prelude" % "1.0.0-RC19",
      "dev.zio" %% "zio-http" % "3.0.0-RC2",
      "com.github.ghostdogpr" %% "caliban" % "2.2.1"
    ),
    excludeDependencies ++= Seq(
      // This causes issues with CrossVersion.for3Use2_13
      "org.scala-lang.modules" % "scala-collection-compat_2.13"
    )
  )
