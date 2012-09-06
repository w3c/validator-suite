package play.api

object Global extends GlobalSettings {

  def vsconf = org.w3.vs.Prod.configuration
  def system = vsconf.system

  override def onStart(app: Application): Unit = {
    org.w3.vs.assertor.CSSValidator.start()
  }
  
  override def onStop(app: Application): Unit = {
    org.w3.vs.assertor.CSSValidator.stop()
    system.shutdown()
  }
  
}
