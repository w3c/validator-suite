package org.w3.util.akkaext

import akka.actor._
import akka.dispatch._
import java.nio.file._
import java.net.URI
import org.w3.util._

object PathAware {

  def apply(root: ActorRef, path: ActorPath): PathAware = new PathAware(root, path)

}

class PathAware(root: ActorRef, path: ActorPath) {

  val jpath = Paths.get(new URI(path.toString).getPath)

  def !(message: Any)(implicit sender: ActorRef = null): Unit =
    root ! Tell(jpath, message)

  def tell(message: Any, sender: ActorRef): Unit =
    root.tell(Tell(jpath, message), sender)

  import akka.pattern.ask

  def ?[A](message: Any)(implicit timeout: akka.util.Timeout, context: ExecutionContext, m: Manifest[A]): FutureVal[Throwable, A] =
    FutureVal.applyTo((root ? Tell(jpath, message)).mapTo[A])
  

}
