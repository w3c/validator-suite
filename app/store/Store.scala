package org.w3.vs.store

import org.w3.vs.model._
import org.w3.vs.observer._
import org.w3.util._

trait Store {
  
  def init(): Either[Throwable, Unit]
  def list: Either[Throwable, Traversable[ObserverState]]
  def get(id: ObserverId): Either[Throwable, ObserverState]
  def save(state: ObserverState): Either[Throwable, Unit]
  
}

