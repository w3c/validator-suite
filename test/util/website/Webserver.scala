package org.w3.util.website

import javax.servlet.http._
import org.eclipse.jetty.server.nio.SelectChannelConnector
import org.eclipse.jetty.server.{ Server }
import org.eclipse.jetty.server.handler.ContextHandlerCollection
import org.eclipse.jetty.webapp.WebAppContext
import org.eclipse.jetty.servlet.{ DefaultServlet, ServletContextHandler, ServletHolder }

case class Webserver(port: Int, servlet: HttpServlet) {

  var server: Server = null
 
  def start(): Unit = {
    server = new Server()

    val context = new ServletContextHandler(ServletContextHandler.SESSIONS)
    context.setContextPath("/")
    server.setHandler(context)

    context.addServlet(new ServletHolder(servlet), "/*")
    server.start()

  }

  def stop(): Unit = if (server != null) {
    server.stop()
  }

}
