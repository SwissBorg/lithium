resolvers += "SwissBorg Nexus".at("https://nexus.sharedborg.com/repository/investmentapp-mvn/")

addSbtPlugin("io.spray"                % "sbt-revolver"    % "0.9.1")
addSbtPlugin("com.typesafe.sbt"        % "sbt-multi-jvm"   % "0.4.0")
addSbtPlugin("com.lightbend.akka.grpc" % "sbt-akka-grpc"   % "0.6.2")
addSbtPlugin("org.scalameta"           % "sbt-scalafmt"    % "2.0.1")
addSbtPlugin("io.get-coursier"         % "sbt-coursier"    % "1.1.0-M11")
addSbtPlugin("ch.epfl.scala"           % "sbt-scalafix"    % "0.9.4")
addSbtPlugin("org.scoverage"           % "sbt-scoverage"   % "1.5.1")
addSbtPlugin("com.swissborg"           % "sbt-swissborg"   % "0.4.0")
addSbtPlugin("org.wartremover"         % "sbt-wartremover" % "2.4.2")
addSbtPlugin("com.thesamet"            % "sbt-protoc"      % "0.99.23")

libraryDependencies += "com.thesamet.scalapb" %% "compilerplugin" % "0.9.0"
