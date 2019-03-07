import com.typesafe.sbt.SbtMultiJvm.multiJvmSettings
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

name := "akka-sbr"

version := "0.0.1"

scalaVersion := "2.12.6"
sbtVersion := "1.2.1"

scalacOptions += "-Ypartial-unification"

val akkaVersion = "2.5.21"
val akkaHTTPVersion = "10.1.7"
val catsVersion = "1.6.0"
val scalatestVersion = "3.0.5"
val monocleVersion = "1.5.0"
val scoptVersion = "4.0.0-RC2"
val shapelessVersion = "2.3.3"

libraryDependencies ++= Seq(
  "eu.timepit" %% "refined" % "0.9.4",
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
  "com.typesafe.akka" %% "akka-distributed-data" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-http" % akkaHTTPVersion,
  "org.typelevel" %% "cats-core" % catsVersion,
  "com.chuusai" %% "shapeless" % shapelessVersion,
  "com.github.julien-truffaut" %% "monocle-core" % monocleVersion,
  "com.github.scopt" %% "scopt" % scoptVersion,
  "me.lyh" %% "magnolia" % "0.10.1-jto",
  "com.github.julien-truffaut" %% "monocle-law" % monocleVersion % Test,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion % Test,
  "org.typelevel" %% "cats-testkit" % "1.1.0" % Test,
  "com.github.alexarchambault" %% "scalacheck-shapeless_1.13" % "1.1.6" % Test,
  "eu.timepit" %% "refined-scalacheck" % "0.9.4" % Test,
  "org.scalatest" %% "scalatest" % scalatestVersion % Test
)

lazy val root = (project in file("."))
  .enablePlugins(MultiJvmPlugin)
  .configs(MultiJvm)
  .settings(multiJvmSettings: _*)
  .settings(parallelExecution in Test := false)

scalacOptions += "-Ywarn-unused"

//wartremoverErrors in (Compile, compile) ++= Warts.allBut(Wart.Any, Wart.Nothing, Wart.ImplicitParameter, Wart.Recursion)
