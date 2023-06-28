import sbt.internal.io.Source

inThisBuild(
  Seq(
    organization := "com.example",
    scalaVersion := "3.3.0",
  ),
)

Global / watchTriggers := Seq(baseDirectory.value.toGlob / ** / "*.scala")

Global / watchSources := {
  val filesToExclude = baseDirectory.value.toGlob / "src/main/resources" / **

  val customSourcesFilter = new FileFilter {
    override def accept(pathname: File): Boolean = filesToExclude.matches(pathname.toPath)

    override def toString = s"CustomSourcesFilter($filesToExclude)"
  }

  (Global / watchSources).value.map { source =>
    new Source(
      source.base,
      source.includeFilter,
      source.excludeFilter || customSourcesFilter,
      source.recursive,
    )
  }
}

val coursierVersion = "2.1.5"
val neo4jVersion = "5.9.0"

lazy val root = (project in file("."))
  .settings(
    name := "dependalyzer",
    scalacOptions ++= Seq(
      "-Yretain-trees",
    ),
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
      "dev.zio" %% "zio-json" % "0.5.0",
      "dev.zio" %% "zio-prelude" % "1.0.0-RC19",
      "dev.zio" %% "zio-http" % "3.0.0-RC2",
      "com.github.ghostdogpr" %% "caliban" % "2.2.1",
    ),
    excludeDependencies ++= Seq(
      // This causes issues with CrossVersion.for3Use2_13
      "org.scala-lang.modules" % "scala-collection-compat_2.13",
    ),
  )
