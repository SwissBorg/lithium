resolvers += Resolver.bintrayRepo("kamon-io", "sbt-plugins")

addSbtPlugin("io.kamon"                % "sbt-aspectj-runner" % "1.1.0")
addSbtPlugin("io.spray"                % "sbt-revolver"       % "0.9.1")

// GRPC
addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc"      % "0.5.0")
addSbtPlugin("com.lightbend.sbt" % "sbt-javaagent" % "0.1.4") // ALPN agent

// Multi-JVM testing
addSbtPlugin("com.typesafe.sbt" % "sbt-multi-jvm" % "0.4.0")

// Kind-projector
addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.9")

// Scalafmt
addSbtPlugin("com.geirsson" % "sbt-scalafmt" % "1.5.1")

// Coursier
addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.1.0-M11")

// Scalafix
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.9.4")

//WartRemover
addSbtPlugin("org.wartremover" % "sbt-wartremover" % "2.4.1")
