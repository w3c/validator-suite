package org.w3.vs

import reactivemongo.api.DefaultDB

trait Database extends ValidatorSuite {
  this: ValidatorSuite =>

  def db: reactivemongo.api.DefaultDB

}

trait DefaultDatabase extends Database {
  this: ValidatorSuite
    with ActorSystem =>

  val driver = new reactivemongo.api.MongoDriver

  val connection = {
    val node = config.getString("application.mongodb.node") getOrElse sys.error("application.mongodb.node")
    driver.connection(Seq(node))
  }

  val db: DefaultDB = {
    val dbName = config.getString("application.mongodb.db-name") getOrElse sys.error("application.mongodb.db-name")
    connection(dbName)(system.dispatchers.lookup("reactivemongo-dispatcher"))
  }

  override def shutdown() {
    import scala.concurrent.duration.Duration
    logger.info("Closing database connection")
    connection.askClose()(Duration(30, "s"))
    driver.close()
    super.shutdown()
  }

}