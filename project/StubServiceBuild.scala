import sbt._

object StubServiceBuild extends Build with MicroService {
  import scala.util.Properties.envOrElse

  val appName = "pla-dynamic-stub"
  val appVersion = envOrElse("PLA_DYNAMIC_STUB_VERSION", "999-SNAPSHOT")

  override lazy val appDependencies: Seq[ModuleID] = AppDependencies()
}

private object AppDependencies {
  import play.PlayImport._
  import play.core.PlayVersion


  private val microserviceBootstrapVersion = "4.2.1"
  private val playHealthVersion = "1.1.0"
  private val playConfigVersion = "2.0.1"
  private val hmrcTestVersion = "1.6.0"
  
  val compile = Seq(
    ws,
    "uk.gov.hmrc" %% "microservice-bootstrap" % microserviceBootstrapVersion,
    "uk.gov.hmrc" %% "play-health" % playHealthVersion,
    "uk.gov.hmrc" %% "play-config" % playConfigVersion,
    "uk.gov.hmrc" %% "play-json-logger" % "2.1.1",
    "uk.gov.hmrc" %% "play-reactivemongo" % "4.5.0"
  )

  trait TestDependencies {
    lazy val scope: String = "test"
    lazy val test : Seq[ModuleID] = ???
  }

  object Test {
    def apply() = new TestDependencies {
      override lazy val test = Seq(
        "uk.gov.hmrc" %% "hmrctest" % hmrcTestVersion % scope,
        "org.scalatest" %% "scalatest" % "2.2.6" % scope,
        "org.pegdown" % "pegdown" % "1.5.0" % scope,
        "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
        "uk.gov.hmrc" %% "domain" % "3.7.0"
      )
    }.test
  }

  def apply() = compile ++ Test()
}


