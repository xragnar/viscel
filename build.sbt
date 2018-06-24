
lazy val viscel = project.in(file("."))
                  .settings(
                    name := "viscel",
                    Settings.main,
                    Libraries.shared,
                    Libraries.main,
                    (Compile / compile) := ((Compile / compile) dependsOn (web / Compile / fastOptJS)).value,
                    Compile / compile := ((compile in Compile) dependsOn (web / Assets / SassKeys.sassify)).value,
                    (Compile / resources) += (web / Compile / fastOptJS / artifactPath).value,
                    (Compile / resources) ++= (web / Assets / SassKeys.sassify).value,
                    sharedSource,
                    )
                  .enablePlugins(JavaServerAppPackaging)
                  .dependsOn(shared % Provided)

lazy val web = project.in(file("web"))
               .enablePlugins(ScalaJSPlugin)
               .settings(
                 name := "web",
                 Settings.common,
                 Libraries.shared,
                 Libraries.js,
                 sharedSource,
                 scalaJSUseMainModuleInitializer := true,
                 )
               .dependsOn(shared % Provided)
               .enablePlugins(SbtSassify)

lazy val shared = project.in(file("shared"))
                  .settings(
                    name := "shared",
                    Settings.common,
                    Libraries.shared,
                    )

lazy val sharedSource = (Compile / unmanagedSourceDirectories) += (shared / Compile / scalaSource).value


lazy val Settings = new {

  lazy val common: sbt.Def.SettingsDefinition = Seq(

    scalaVersion := "2.12.6",

    maxErrors := 10,
    shellPrompt := { state => Project.extract(state).currentRef.project + "> " },


    Compile / compile / scalacOptions ++= Seq(
      "-deprecation",
      "-encoding", "UTF-8",
      "-unchecked",
      "-feature",
      "-target:jvm-1.8",
      "-Xlint",
      "-Xfuture",
      //"-Xlog-implicits" ,
      //"-Yno-predef" ,
      //"-Yno-imports" ,
      "-Xfatal-warnings",
      //"-Yinline-warnings" ,
      "-Yno-adapted-args",
      //"-Ywarn-dead-code" ,
      "-Ywarn-nullary-override",
      "-Ywarn-nullary-unit",
      "-Ywarn-numeric-widen",
      //"-Ywarn-value-discard" ,
      ),
    )

  lazy val main = common ++ Seq(

    fork := true,

    javaOptions ++= Seq(
      "-verbose:gc",
      "-XX:+PrintGCDetails",
      //"-Xverify:none",
      //"-server",
      //"-Xms16m",
      //"-Xmx256m",
      //"-Xss1m",
      //"-XX:MinHeapFreeRatio=5",
      //"-XX:MaxHeapFreeRatio=10",
      //"-XX:NewRatio=12",
      //"-XX:+UseSerialGC",
      //"-XX:+UseParallelGC",
      //"-XX:+UseParallelOldGC",
      //"-XX:+UseConcMarkSweepGC",
      //"-XX:+PrintTenuringDistribution",
      ))

}

lazy val Libraries = new {

  private val rescalaVersion = "0.23.0"

  lazy val shared: Def.SettingsDefinition = Seq(
    resolvers += Resolver.bintrayRepo("rmgk", "maven"),
    resolvers += Resolver.bintrayRepo("stg-tud", "maven"),
    libraryDependencies ++= (Seq(
      "de.rmgk" %%% "logging" % "0.2.1",
      "de.tuda.stg" %%% "rescala" % rescalaVersion,
      "com.lihaoyi" %%% "scalatags" % "0.6.7"
    ) ++ Seq("communication", "communicator-ws-akka", "serializer-circe")
         .map(n => "de.tuda.stg" %%% s"scala-loci-$n" % "0.2.0")
      ++ Seq("core", "generic", "generic-extras", "parser")
         .map(n => "io.circe" %%% s"circe-$n" % "0.9.3")
      ).map(_ exclude("org.scala-lang", "scala-reflect"))
  )

  lazy val main: Def.SettingsDefinition = libraryDependencies ++= Seq(
    "org.scalactic" %% "scalactic" % "3.0.5",
    "org.jsoup" % "jsoup" % "1.11.3",
    "com.monovore" %% "decline" % "0.4.1",
    "org.asciidoctor" % "asciidoctorj" % "1.5.6",
    "org.scalatest" %% "scalatest" % "3.0.5" % "test",
    "org.scalacheck" %% "scalacheck" % "1.14.0" % "test",
    "com.typesafe.akka" %% "akka-stream" % "2.5.13",
    ).++(
    Seq("akka-http-core", "akka-http").map(n => "com.typesafe.akka" %% n % "10.1.3")
  ).map(_ exclude("org.scala-lang", "scala-reflect"))

  lazy val js: Def.SettingsDefinition = libraryDependencies ++= Seq(
    "org.scala-js" %%% "scalajs-dom" % "0.9.4",
    "de.tuda.stg" %%% "rescalatags" % rescalaVersion,
    "org.webjars.npm" % "purecss" % "1.0.0",
    )


}
