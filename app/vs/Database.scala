package org.w3.vs

import reactivemongo.api.DefaultDB

trait Database {

  def db: reactivemongo.api.DefaultDB

}
