package org.w3.vs.store

import org.scalatest.{Filter => _, _}
import org.scalatest.matchers.MustMatchers
import java.io._
import org.w3.vs.model._
import org.w3.util.{File => _, _}
import org.w3.vs.observer._
import java.nio.file.Paths

abstract class StoreSpec extends WordSpec with MustMatchers with EitherValues {
  
  val strategy =
    EntryPointStrategy(
      uuid=java.util.UUID.randomUUID(),
      name="w3.org",
      entrypoint=URL("http://www.w3.org/"),
      distance=1,
      linkCheck=true,
      filter=Filter(include=Everything, exclude=Nothing))
  
  val job = Job(strategy)
  
  val store: Store
  
  "a store" must {
    
    "be initializable" in {
      val r = store.init()
      r must be ('right)
    }
    
    val os1 = ObserverState(ObserverId(), job)
    val os2 = ObserverState(ObserverId(), job)
    val os3 = ObserverState(ObserverId(), job)
    
    "save new ObserverStates" in {
      val op1 = store.save(os1)
      op1 must be ('right)
      val op2 = store.save(os2)
      op2 must be ('right)
      val op3 = store.save(os3)
      op3 must be ('right)
    }
    
    "get states" in {
      val g1 = store.get(os1.id).right.get
      g1 must equal (os1)
      val g2 = store.get(os2.id).right.get
      g2 must equal (os2)
      val g3 = store.get(os3.id).right.get
      g3 must equal (os3)
    }
    
    "list ObserverStates" in {
      val os = store.list.right.get
      os must contain (os1)
      os must contain (os2)
      os must contain (os3)
    }
    
  }
  
  
}

class FileStoreSpec extends StoreSpec {
  
  lazy val storeDir = {
    val dir = Paths.get(System.getProperty("java.io.tmpdir")).resolve("filestore").toFile()
    if (! dir.exists) dir.mkdir()
    dir
  }
  
  lazy val store = new FileStore(storeDir)
  
}

class MemoryStoreSpec extends StoreSpec {
  
  lazy val store = new MemoryStore
  
}
