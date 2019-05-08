import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm
import com.typesafe.sbt.SbtMultiJvm.multiJvmSettings

organization := "com.swissborg"
name := "akka-sbr"

version := "0.0.1"

scalaVersion := "2.12.8"
sbtVersion := "1.2.1"

scalacOptions ++=
  Seq(
    "-encoding",
    "UTF-8",
    "-feature",
    "-language:existentials",
    "-language:higherKinds",
    "-language:implicitConversions",
    "-language:postfixOps",
    "-language:experimental.macros",
    "-unchecked",
    "-Ypartial-unification",
    "-Ywarn-dead-code",
    "-Ywarn-unused",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard",
    "-Xfuture",
    "-Yno-adapted-args",
//    "-Xfatal-warnings",
    "-deprecation"
  )

val akkaVersion                = "2.5.22"
val akkaHTTPVersion            = "10.1.8"
val catsVersion                = "1.6.0"
val catsEffectVersion          = "1.2.0"
val scalatestVersion           = "3.0.7"
val monocleVersion             = "1.5.0"
val scoptVersion               = "4.0.0-RC2"
val shapelessVersion           = "2.3.3"
val refinedVersion             = "0.9.5"
val pureConfigVersion          = "0.10.2"
val scalacheckShapelessVersion = "1.1.8"
val refinedScalacheckVersion   = "0.9.5"
val logbackVersion             = "1.2.3"

libraryDependencies ++= Seq(
  "eu.timepit"                 %% "refined"                   % refinedVersion,
  "eu.timepit"                 %% "refined-cats"              % refinedVersion,
  "eu.timepit"                 %% "refined-pureconfig"        % refinedVersion,
  "eu.timepit"                 %% "refined-shapeless"         % refinedVersion,
  "com.typesafe.akka"          %% "akka-actor"                % akkaVersion,
  "com.typesafe.akka"          %% "akka-cluster"              % akkaVersion,
  "com.typesafe.akka"          %% "akka-cluster-tools"        % akkaVersion,
  "com.typesafe.akka"          %% "akka-distributed-data"     % akkaVersion,
  "com.typesafe.akka"          %% "akka-stream"               % akkaVersion,
  "com.typesafe.akka"          %% "akka-slf4j"                % akkaVersion,
  "ch.qos.logback"             % "logback-classic"            % logbackVersion,
  "com.typesafe.akka"          %% "akka-http"                 % akkaHTTPVersion,
  "org.typelevel"              %% "cats-core"                 % catsVersion,
  "org.typelevel"              %% "cats-effect"               % catsEffectVersion,
  "org.typelevel"              %% "cats-testkit"              % catsVersion,
  "com.chuusai"                %% "shapeless"                 % shapelessVersion,
  "com.github.julien-truffaut" %% "monocle-core"              % monocleVersion,
  "com.github.pureconfig"      %% "pureconfig"                % pureConfigVersion,
  "com.github.scopt"           %% "scopt"                     % scoptVersion,
  "com.github.julien-truffaut" %% "monocle-law"               % monocleVersion % Test,
  "com.typesafe.akka"          %% "akka-testkit"              % akkaVersion % Test,
  "com.typesafe.akka"          %% "akka-multi-node-testkit"   % akkaVersion % Test,
  "com.github.alexarchambault" %% "scalacheck-shapeless_1.13" % scalacheckShapelessVersion % Test,
  "eu.timepit"                 %% "refined-scalacheck"        % refinedScalacheckVersion % Test,
  "org.scalatest"              %% "scalatest"                 % scalatestVersion % Test
)

lazy val root = (project in file("."))
  .enablePlugins(MultiJvmPlugin)
  .configs(MultiJvm)
  .settings(multiJvmSettings: _*)
  .settings(parallelExecution in Test := false)

//wartremoverErrors in (Compile, compile) ++= Warts.allBut(Wart.Any, Wart.Nothing, Wart.ImplicitParameter, Wart.Recursion)
testOptions in Test += Tests.Argument("-oF")

// SemanticDB
//addCompilerPlugin(scalafixSemanticdb)

coverageEnabled := true

parallelExecution in MultiJvm := false

resolvers += Resolver.sonatypeRepo("releases")
addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.0")
