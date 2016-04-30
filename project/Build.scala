import com.typesafe.sbt.packager.archetypes.JavaAppPackaging
import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import sbt.Keys._
import sbt._

object Build extends sbt.Build {

	lazy val viscel = project.in(file("."))
		.settings(name := "viscel")
		.settings(Settings.main: _*)
		.settings(Libraries.main: _*)
		//.settings(compile in Compile <<= (compile in Compile) dependsOn (fullOptJS in(js, Compile)))
		.settings(resources in Compile += artifactPath.in(js, Compile, fullOptJS).value)
		.enablePlugins(JavaAppPackaging)
		.dependsOn(scribe)
		.dependsOn(selection)
		.dependsOn(shared % Provided)
		.settings(Settings.sharedSource)


	lazy val js = project.in(file("js"))
		.settings(name := "viscel-js")
		.enablePlugins(ScalaJSPlugin)
		.settings(Settings.common: _*)
		.settings(Libraries.js: _*)
		.dependsOn(shared % Provided)
		.settings(Settings.sharedSource)

	lazy val shared = project.in(file("shared"))
		.settings(name := "viscel-shared")
		.settings(Settings.common: _*)
		.settings(libraryDependencies ++= Libraries.shared.value)

	lazy val scribe = project.in(file("scribe"))
		.settings(name := "scribe")
		.settings(Settings.common: _*)
		.settings(Libraries.scribe: _*)
		.dependsOn(selection)

	lazy val selection = project.in(file("selection"))
		.settings(name := "selection")
		.settings(Settings.common: _*)
		.settings(Libraries.selection: _*)

}

object Settings {

	lazy val sharedSource = unmanagedSourceDirectories in Compile += (scalaSource in (Build.shared, Compile)).value

	lazy val common = List(

		version := "6.2.0",
		scalaVersion := "2.11.8",

		scalacOptions ++=
			"-deprecation" ::
			"-encoding" :: "UTF-8" ::
			"-unchecked" ::
			"-feature" ::
			"-target:jvm-1.7" ::
			"-Xlint" ::
			"-Xfuture" ::
			//"-Xlog-implicits" ::
			//"-Yno-predef" ::
			//"-Yno-imports" ::
			"-Xfatal-warnings" ::
			"-Yinline-warnings" ::
			"-Yno-adapted-args" ::
			//"-Ywarn-dead-code" ::
			"-Ywarn-nullary-override" ::
			"-Ywarn-nullary-unit" ::
			//"-Ywarn-numeric-widen" ::
			//"-Ywarn-value-discard" ::
			Nil,

		resolvers ++=
			("Sonatype OSS Releases" at "http://oss.sonatype.org/content/repositories/releases/") ::
			("Sonatype OSS Snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/") ::
			// ("spray nightlies repo" at "http://nightlies.spray.io") ::
			("spray repo" at "http://repo.spray.io") ::
			("Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/") ::
			// Resolver.bintrayRepo("rmgk", "maven") ::
			Resolver.url("bintray rmgk maven", url("http://dl.bintray.com/rmgk/maven"))(Resolver.ivyStylePatterns) ::
			Nil)

	lazy val main: List[Def.Setting[_]] = common ++ List(

		fork := true,

		javaOptions ++=
			"-verbose:gc" ::
			"-XX:+PrintGCDetails" ::
			//"-Xverify:none" ::
			//"-server" ::
			//"-Xms16m" ::
			//"-Xmx256m" ::
			//"-Xss1m" ::
			//"-XX:MinHeapFreeRatio=5" ::
			//"-XX:MaxHeapFreeRatio=10" ::
			//"-XX:NewRatio=12" ::
			//"-XX:+UseSerialGC" ::
			//"-XX:+UseParallelGC" ::
			//"-XX:+UseParallelOldGC" ::
			//"-XX:+UseConcMarkSweepGC" ::
			//"-XX:+PrintTenuringDistribution" ::
			Nil,

		initialCommands in console :=
			"""import akka.actor.{ ActorSystem, Props, Actor }
				|import akka.io.IO
				|import akka.util.Timeout
				|import org.jsoup._
				|import org.neo4j.graphdb._
				|import scala.collection.JavaConversions._
				|import scala.concurrent._
				|import scala.concurrent.duration._
				|import scala.concurrent.ExecutionContext.Implicits.global
				|import spray.can._
				|import spray.client.pipelining._
				|import spray.http._
				|import viscel._
				|import viscel.server._
				|import viscel.store._
				|import scala.Predef._
				|import viscel.ReplUtil
				|import viscel.narration._
				|import viscel.narration.narrators._
			""".stripMargin)

}

object Libraries {

	lazy val main: List[Def.Setting[_]] = List(libraryDependencies ++=
		neo ::: spray ::: akka :::
		jline ::: jopt  ::: scalactic ::: shared.value)

	lazy val js: List[Def.Setting[_]] = List(libraryDependencies ++=
		scalajsdom.value ::: shared.value ::: rescala.value)

	lazy val shared: Def.Initialize[List[ModuleID]] = Def.setting(
		scalatags.value ::: upickle.value)

	lazy val scribe: List[Def.Setting[_]] = List(libraryDependencies ++= neo ++ spray_client ++ akka ++ scalactic ++ jsoup)

	lazy val selection: List[Def.Setting[_]] = List(libraryDependencies ++= scalactic ++ jsoup)


	val jsoup = "org.jsoup" % "jsoup" % "1.8.1" :: Nil

	// gpl3
	val neo = List("kernel", "lucene-index").map(module => "org.neo4j" % s"neo4j-$module" % "3.0.0")

	// apache 2
	val spray = List("spray-routing").map(n => "io.spray" %% n % "1.3.3")
	val spray_client = List("spray-client").map(n => "io.spray" %% n % "1.3.3")

	val akka = List("akka-actor").map(n => "com.typesafe.akka" %% n % "2.4.4")

	val jline = "jline" % "jline" % "2.14.1" :: Nil

	val jopt = "net.sf.jopt-simple" % "jopt-simple" % "5.0.1" :: Nil

	val scalactic = ("org.scalactic" %% "scalactic" % "2.2.6" exclude("org.scala-lang", "scala-reflect")) :: Nil

	val scalatags = Def.setting("com.lihaoyi" %%% "scalatags" % "0.5.5" :: Nil)

	val upickle = Def.setting("com.lihaoyi" %%% "upickle" % "0.4.0" :: Nil)

	val scalajsdom = Def.setting(("org.scala-js" %%% "scalajs-dom" % "0.8.2") :: Nil)

	val rescala = Def.setting(("de.tuda.stg" %%% "rescala" % "0.14.0") :: Nil)

}
