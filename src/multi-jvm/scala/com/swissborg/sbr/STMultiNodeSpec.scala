package com.swissborg.sbr

import akka.remote.testkit.MultiNodeSpecCallbacks
import org.scalatest.{BeforeAndAfterAll, FreeSpecLike, Matchers}

trait STMultiNodeSpec extends MultiNodeSpecCallbacks with FreeSpecLike with Matchers with BeforeAndAfterAll {
  override def beforeAll(): Unit = multiNodeSpecBeforeAll()
  override def afterAll(): Unit = multiNodeSpecAfterAll()
}