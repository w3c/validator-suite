//package org.w3.vs
//
//import java.net.URI
//import java.net.URISyntaxException
//import akka.actor.Channel
//import akka.actor.ActorKilledException
//import akka.actor.ActorRef
//import akka.actor.LocalActorRef
//import akka.dispatch.Future
//import akka.dispatch.MessageInvocation
//import akka.dispatch.MessageQueue
//import akka.actor.Actor
//import akka.dispatch.CompletableFuture
//import akka.actor.ActorInitializationException
//import akka.dispatch.DefaultCompletableFuture
//import akka.dispatch.FutureTimeoutException
//import akka.actor.NullChannel
//import akka.actor.UntypedChannel
//
///**
// * This file contains random utility functions.
// * Some of them may be interesting.
// */
//package object common {
//
//    // Class that adds replyWith to Akka channels
//    class EnhancedChannel[-T](underlying: Channel[T]) {
//        /**
//         * Replies to a channel with the result or exception from
//         * the passed-in future
//         */
//        def replyWith[A <: T](f: Future[A])(implicit sender: UntypedChannel) = {
//            f.onComplete({ f =>
//                f.value.get match {
//                    case Left(t) =>
//                        underlying.sendException(t)
//                    case Right(v) =>
//                        underlying.tryTell(v)
//                }
//            })
//        }
//    }
//
//    // implicitly create an EnhancedChannel wrapper to add methods to the
//    // channel
//    implicit def enhanceChannel[T](channel: Channel[T]): EnhancedChannel[T] = {
//        new EnhancedChannel(channel)
//    }
//
//    private def getMailbox(self: ActorRef) = {
//        self match {
//            // LocalActorRef.mailbox is public but
//            // ActorRef.mailbox is not; not sure if
//            // it's deliberate or a bug, but we use it...
//            // this code can be deleted with newer Akka versions
//            // that have fix https://www.assembla.com/spaces/akka/tickets/894
//            case local: LocalActorRef =>
//                local.mailbox
//            case _ =>
//                throw new Exception("Can't get mailbox on this ActorRef: " + self)
//        }
//    }
//
//    private def invocations(mq: MessageQueue): Stream[MessageInvocation] = {
//        val mi = mq.dequeue
//        if (mi eq null)
//            Stream.empty
//        else
//            Stream.cons(mi, invocations(mq))
//    }
//
//    private def sendExceptionsToMailbox(mailbox: AnyRef) = {
//        mailbox match {
//            case mq: MessageQueue =>
//                invocations(mq) foreach { mi =>
//                    mi.channel.sendException(new ActorKilledException("Actor is about to suicide"))
//                }
//            case _ =>
//                throw new Exception("Don't know how to iterate over mailbox: " + mailbox)
//        }
//    }
//
//    // Akka 2.0 has a fix where, on stopping an actor,
//    // the sender gets an exception;
//    // see https://www.assembla.com/spaces/akka/tickets/894
//    // In 1.2, we use this temporary workaround to simulate the 2.0 behavior.
//    def stopActorNotifyingMailbox(self: ActorRef) = {
//        val mailbox = getMailbox(self)
//        self.stop
//        sendExceptionsToMailbox(mailbox)
//    }
//
//    def tryAsk(actor: ActorRef, message: Any)(implicit channel: UntypedChannel = NullChannel, timeout: Actor.Timeout = Actor.defaultTimeout): CompletableFuture[Any] = {
//        // "?" will throw by default on a stopped actor; we want to put an exception
//        // in the future instead to avoid special cases
//        try {
//            actor ? message
//        } catch {
//            case e: ActorInitializationException =>
//                val f = new DefaultCompletableFuture[Any]()
//                f.completeWithException(new ActorKilledException("Actor was not running, immediate timeout"))
//                f
//        }
//    }
//
//    private def stripSlash(s: String) =
//        if (s == "")
//            null
//        else if (s.startsWith("/"))
//            s.substring(1)
//        else
//            s
//
//    def expandURI(s: String, defaults: URIParts): Option[URIParts] = {
//        try {
//            val uri = new URI(s)
//
//            val host = Option(uri.getHost) orElse (defaults.host)
//            val port = (if (uri.getPort == -1) None else Some(uri.getPort)) orElse (defaults.port)
//
//            // URI has the "/" in front of the path but URIParts strips it off.
//            val path = Option(stripSlash(uri.getPath)) orElse (defaults.path)
//            val userInfo = Option(uri.getUserInfo)
//            val (user, password) = userInfo map { ui =>
//                if (ui.contains(":")) {
//                    val a = ui.split(":", 2)
//                    (Some(a(0)) -> Some(a(1)))
//                } else {
//                    (Some(ui) -> defaults.password)
//                }
//            } getOrElse (defaults.user -> defaults.password)
//
//            Some(URIParts(scheme = uri.getScheme, user = user, password = password,
//                host = host, port = port, path = path))
//        } catch {
//            case e: URISyntaxException =>
//                None
//        }
//    }
//}
