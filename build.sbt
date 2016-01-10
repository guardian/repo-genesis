name := "repo-genesis"

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.7"

updateOptions := updateOptions.value.withCachedResolution(true)

lazy val root = (project in file(".")).enablePlugins(
  PlayScala,
  BuildInfoPlugin
).settings(
  buildInfoKeys := Seq[BuildInfoKey](
    name,
    BuildInfoKey.constant("gitCommitId", Option(System.getenv("SOURCE_VERSION")) getOrElse(try {
      "git rev-parse HEAD".!!.trim
    } catch { case e: Exception => "unknown" }))
  ),
  buildInfoPackage := "app"
)

TwirlKeys.templateImports ++= Seq(
  "com.madgag.github.Implicits._"
)

routesImport ++= Seq("lib._","com.madgag.github._")

resolvers ++= Seq(
  Resolver.sonatypeRepo("releases")
)

libraryDependencies ++= Seq(
  cache,
  filters,
  "com.madgag" %% "play-git-hub" % "3.6",
  "com.typesafe.akka" %% "akka-agent" % "2.3.2",
  "net.kencochrane.raven" % "raven-logback" % "6.0.0",
  "org.webjars" % "bootstrap" % "3.3.5",
  "com.adrianhurt" %% "play-bootstrap3" % "0.4.4-P24",
  "org.webjars.bower" % "octicons" % "3.1.0",
  "org.webjars.bower" % "select2" % "3.5.4",
  "org.webjars.bower" % "select2-bootstrap-css" % "1.4.6",
  "org.webjars.npm" % "handlebars" % "3.0.3",
  "com.netaporter" %% "scala-uri" % "0.4.7",
  "com.github.scala-incubator.io" %% "scala-io-file" % "0.4.3-1",
  "org.scalatestplus" %% "play" % "1.4.0-M4" % "test"
)

sources in (Compile,doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false
