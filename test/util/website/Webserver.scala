package org.w3.vs.util

import javax.servlet.http._
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.{ ServletContextHandler, ServletHolder }

case class Webserver(port: Int, servlet: HttpServlet) {

  val logger = play.api.Logger(classOf[Webserver])

  // why a var ? why doesn't a Webserver simply extends a Server ? why a case class ?
  var server: Server = null
 
  def start(): Unit = {

    logger.info(s"Creating a new server on port ${port}")

    server = new Server(port)

    val context = new ServletContextHandler(ServletContextHandler.SESSIONS)
    context.setContextPath("/")
    server.setHandler(context)

    context.addServlet(new ServletHolder(servlet), "/*")
    server.start()
  }

  def stop(): Unit = if (server != null) {
    logger.info(s"Closing server on port ${port}")
    server.stop()
  }

}
