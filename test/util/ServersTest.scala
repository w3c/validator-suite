package org.w3.vs.util

import org.scalatest.{Suite, BeforeAndAfterAll}
import org.w3.vs.HttpClient

trait ServersTest extends BeforeAndAfterAll { this: Suite with BeforeAndAfterAll =>

  def servers: Seq[Webserver]

  override def beforeAll() {
    super.beforeAll()
    servers foreach { _.start() }
  }

  override def afterAll() {
    servers foreach { _.stop() }
    super.afterAll()
  }

}

