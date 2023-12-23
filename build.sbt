name := "repo-genesis"

version := "1.0-SNAPSHOT"

scalaVersion := "2.13.11"

updateOptions := updateOptions.value.withCachedResolution(true)

lazy val root = (project in file(".")).enablePlugins(
  PlayScala,
  BuildInfoPlugin
).settings(
  buildInfoKeys := Seq[BuildInfoKey](
    name,
    "gitCommitId" -> Option(System.getenv("SOURCE_VERSION")).getOrElse("unknown")
  ),
  buildInfoPackage := "app"
)

TwirlKeys.templateImports ++= Seq(
  "com.madgag.github.Implicits._"
)

routesImport ++= Seq("lib._","com.madgag.github._")

resolvers ++= Resolver.sonatypeOssRepos("releases")

libraryDependencies ++= Seq(
  filters,
  "com.madgag" %% "play-git-hub" % "5.5",
  "com.softwaremill.macwire" %% "macros" % "2.5.8" % Provided, // slight finesse: 'provided' as only used for compile
  "org.webjars" % "bootstrap" % "5.3.0",
  "com.adrianhurt" %% "play-bootstrap" % "1.6.1-P28-B4",
  "org.webjars.bower" % "octicons" % "7.1.0",
  "org.webjars.bower" % "select2" % "4.0.13",
  "org.webjars.bower" % "select2-bootstrap-css" % "1.4.6",
  "io.lemonlabs" %% "scala-uri" % "4.0.3",
  "org.scalatestplus.play" %% "scalatestplus-play" % "5.1.0" % Test
)

Compile / doc / sources := Seq.empty

Compile / packageDoc / publishArtifact := false
