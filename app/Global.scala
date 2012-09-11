import play.api._

object Global extends GlobalSettings {

  val conf = org.w3.vs.Prod.configuration
  import conf._

  override def onStart(app: Application): Unit = {
    org.w3.vs.assertor.CSSValidator.start()
  }
  
  override def onStop(app: Application): Unit = {
    org.w3.vs.assertor.CSSValidator.stop()
    store.shutdown()
    system.shutdown()
  }
  
}
