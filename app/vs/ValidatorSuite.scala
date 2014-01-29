package org.w3.vs

import assertor.LocalValidators
import play.api.{ Logger, Mode, Configuration }
import play.api.Mode._
import akka.util.Timeout
import akka.actor._
import org.w3.vs.actor.{ RunsActor, RunEventBusActor, RunEventBus }
import reactivemongo.api.DefaultDB
import org.w3.vs.web.Cache
import com.ning.http.client.{ AsyncHttpClient, AsyncHttpClientConfig }

trait ValidatorSuite extends Database {

  def mode: Mode
  val name: String = "W3C Validator Suite"

  def start(): Unit = {
    logger.info("Application starting")
    LocalValidators.start()
  }

  def shutdown(): Unit = {
    logger.info("Application shut down")
    shutdownAkkaSystem()
    shutdownDatabase()
    shutdownHttpClient()
    LocalValidators.stop()
  }

  protected lazy val logger: Logger = Logger(name + (mode match {
    case Test => " [Test]"
    case Dev =>  " [Dev]"
    case _ => ""
  }))

  lazy val config: Configuration = {
    import java.io.File
    import com.typesafe.config.{ConfigParseOptions, ConfigFactory}

    def load(fileName: String): Configuration =
      Configuration(ConfigFactory.load(ConfigFactory.parseFileAnySyntax(new File(s"conf/${fileName}"))))

    mode match {
      case Test => load("application.conf") ++ load("application-test.conf")
      case Dev =>  load("application.conf") ++ load("application-dev.conf")
      // will look for application.conf on the class path
      case _ => Configuration(ConfigFactory.load(ConfigParseOptions.defaults().setAllowMissing(false)))
    }
  }

  /* ActorSystem */

  implicit lazy val timeout: Timeout = {
    import scala.concurrent.duration.Duration
    val r = """^(\d+)([^\d]+)$""".r
    val r(timeoutS, unitS) = config.getString("application.timeout") getOrElse sys.error("application.timeout")
    Timeout(Duration(timeoutS.toInt, unitS))
  }

  implicit lazy val system: ActorSystem = {
    val vs = ActorSystem("vs", config.getConfig("application.vs").map(_.underlying) getOrElse sys.error("application.vs"))
    val listener = vs.actorOf(Props(new Actor {
      def receive = {
        case d: DeadLetter => logger.debug("DeadLetter - sender: %s, recipient: %s, message: %s" format(d.sender.toString, d.recipient.toString, d.message.toString))
      }
    }))
    vs.eventStream.subscribe(listener, classOf[DeadLetter])
    vs
  }

  def shutdownAkkaSystem(): Unit = {
    logger.info(s"Shuting down Akka system: ${system.name}")
    system.shutdown()
    system.awaitTermination()
  }

  /* RunEvents */

  lazy val runEventBus: RunEventBus = {
    val actorRef = system.actorOf(Props(classOf[RunEventBusActor]), "runevent-bus")
    RunEventBus(actorRef)
  }

  lazy val runsActorRef: ActorRef =
    system.actorOf(Props(new RunsActor()(this)), "runs")

  /* HttpClient */
  val userAgent = config.getString("application.http-client.user-agent") getOrElse "Validator Suite"
  // Used by form validation
  lazy val formHttpClient: AsyncHttpClient = {
    val builder = new AsyncHttpClientConfig.Builder()
    val asyncHttpConfig =
      builder
        .setFollowRedirects(false)
        .setMaximumConnectionsTotal(100)
        .setMaximumConnectionsPerHost(5)
        .setIdleConnectionTimeoutInMs(10000)
        .setIdleConnectionInPoolTimeoutInMs(10000)
        .setWebSocketIdleTimeoutInMs(10000)
        .setRequestTimeoutInMs(30000)
        .setConnectionTimeoutInMs(30000)
        .setUserAgent(userAgent)
        .build
    new AsyncHttpClient(asyncHttpConfig)
  }

  lazy val httpCacheOpt: Option[Cache] = Cache(config)

  /* Database */

  lazy val driver = new reactivemongo.api.MongoDriver

  lazy val connection = {
    val node = config.getString("application.mongodb.node") getOrElse sys.error("application.mongodb.node")
    driver.connection(Seq(node))
  }

  lazy val db: DefaultDB = {
    val dbName = config.getString("application.mongodb.db-name") getOrElse sys.error("application.mongodb.db-name")
    connection(dbName)(system.dispatchers.lookup("reactivemongo-dispatcher"))
  }

  def shutdownDatabase(): Unit = {
    import scala.concurrent.duration.Duration
    logger.info("Closing database connection")
    connection.askClose()(Duration(30, "s"))
    driver.close()
  }

  def shutdownHttpClient(): Unit = {
    logger.info("Closing HTTPClient")
    formHttpClient.close()
  }


}
