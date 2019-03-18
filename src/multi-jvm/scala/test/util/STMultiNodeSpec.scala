package test.util

import akka.remote.testkit.MultiNodeSpecCallbacks
import org.scalatest.{BeforeAndAfterAll, Matchers, FreeSpecLike}

trait STMultiNodeSpec extends MultiNodeSpecCallbacks with FreeSpecLike with Matchers with BeforeAndAfterAll {
  override def beforeAll(): Unit = multiNodeSpecBeforeAll()
  override def afterAll(): Unit = multiNodeSpecAfterAll()
}
