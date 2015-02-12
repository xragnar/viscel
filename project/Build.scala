import sbt.Keys._
import sbt._

object Build extends sbt.Build {

	lazy val crawl = project.in(file("."))
		.settings(name := "viscelcrawl")
		.settings(Settings.main: _*)
		.settings(Libraries.main: _*)

}

object Settings {
	lazy val main = List(

		version := "0.1.0",
		scalaVersion := "2.11.5",

		scalacOptions ++= (
			"-deprecation" ::
				"-encoding" :: "UTF-8" ::
				"-unchecked" ::
				"-feature" ::
				"-target:jvm-1.7" ::
				"-Xlint" ::
				"-Xfuture" ::
				//"-Xlog-implicits" ::
				"-Yno-predef" ::
				//"-Yno-imports" ::
				"-Xfatal-warnings" ::
				"-Yinline-warnings" ::
				"-Yno-adapted-args" ::
				//"-Ywarn-dead-code" ::
				"-Ywarn-nullary-override" ::
				"-Ywarn-nullary-unit" ::
				//"-Ywarn-numeric-widen" ::
				//"-Ywarn-value-discard" ::
				Nil),

		resolvers ++= (
			("Sonatype OSS Releases" at "http://oss.sonatype.org/content/repositories/releases/") ::
				("Sonatype OSS Snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/") ::
				// ("spray nightlies repo" at "http://nightlies.spray.io") ::
				("spray repo" at "http://repo.spray.io") ::
				("Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/") ::
				Nil))
}

object Libraries {


	lazy val main: List[Def.Setting[_]] = List(libraryDependencies ++= neo ++ spray ++ akka ++
		 scalatest ++ scalactic ++ jsoup)


	// gpl3
	val neo = {
		val neoVersion = "2.1.7"
		Seq("kernel", "lucene-index").map(module => "org.neo4j" % s"neo4j-$module" % neoVersion)
	}

	// apache 2
	val spray =
		List("spray-caching", "spray-can", "spray-client", "spray-http", "spray-httpx", "spray-routing", "spray-util")
			.map(n => "io.spray" %% n % "1.3.2")

	val akka =
		List("akka-actor", "akka-slf4j")
			.map(n => "com.typesafe.akka" %% n % "2.3.9")


	val scalatest = ("org.scalatest" %% "scalatest" % "2.2.4" % Test) :: Nil
	val scalactic = ("org.scalactic" %% "scalactic" % "2.2.4" exclude("org.scala-lang", "scala-reflect")) :: Nil
	val jsoup = "org.jsoup" % "jsoup" % "1.8.1" :: Nil

}
