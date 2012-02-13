package org.w3.vs.store

import org.specs2.mutable.Specification
import java.io._
import org.specs2.mutable.Before
import org.w3.vs.model._
import org.w3.util.{File => _, _}
import org.w3.vs.observer._
import org.specs2.matcher.BeEqualTo

abstract class StoreSpec() extends Specification {
  
  val strategy =
    EntryPointStrategy(
      uuid=java.util.UUID.randomUUID(),
      name="w3.org",
      entrypoint=URL("http://www.w3.org/"),
      distance=1,
      linkCheck=true,
      filter=Filter(include=Everything, exclude=Nothing))
  
  val store: Store
  
  "a store" should {
    
    "be initializable" in {
      val r = store.init()
      r must beRight ()
    }
    
    val os1 = ObserverState(ObserverId(), strategy)
    val os2 = ObserverState(ObserverId(), strategy)
    val os3 = ObserverState(ObserverId(), strategy)
    
    "save new ObserverStates" in {
      val op1 = store.save(os1)
      op1 must beRight ()
      val op2 = store.save(os2)
      op2 must beRight ()
      val op3 = store.save(os3)
      op3 must beRight ()
    }
    
    "get states" in {
      val g1 = store.get(os1.id).right.get
      g1 must beEqualTo (os1)
      val g2 = store.get(os2.id).right.get
      g2 must beEqualTo (os2)
      val g3 = store.get(os3.id).right.get
      g3 must beEqualTo (os3)
    }
    
    "list ObserverStates" in {
      val os = store.list.right.get
      os must containAllOf(List(os1, os2, os3))
    }
    
  }
  
  
}

object FileStoreSpec extends StoreSpec() {
  
  lazy val storeDir = {
    val dir = new File("/tmp/filestore")
    if (! dir.exists) dir.mkdir()
    dir
  }
  
  lazy val store = new FileStore(storeDir)
  
}

object MemoryStoreSpec extends StoreSpec() {
  
  lazy val store = new MemoryStore
  
}
