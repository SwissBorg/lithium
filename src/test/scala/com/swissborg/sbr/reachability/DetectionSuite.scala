package com.swissborg.sbr.reachability

import akka.actor.Address
import akka.cluster.UniqueAddress
import cats.data.NonEmptySet
import com.swissborg.sbr.implicits._
import com.swissborg.sbr.reachability.Detection._
import org.scalatest.{Matchers, WordSpec}

class DetectionSuite extends WordSpec with Matchers {
  val aa = UniqueAddress(Address("akka.tcp", "sys", "a", 2552), 1L)
  val bb = UniqueAddress(Address("akka.tcp", "sys", "b", 2552), 1L)

  "Detection" must {
    "become protested" in {
      val unprotested = Unprotested(0)
      unprotested.addProtester(aa) should ===(Protested(NonEmptySet.one(aa), 0))
    }

    "stay unprotested" in {
      val unprotested = Unprotested(0)
      unprotested.removeProtester(aa) should ===(unprotested)
    }

    "become unprotested" in {
      val protested = Protested.one(aa, 0)
      protested.removeProtester(aa) should ===(Unprotested(0))
    }

    "stay protested" in {
      val protested = Protested.one(aa, 0).addProtester(bb)
      protested.removeProtester(bb) should ===(Protested(NonEmptySet.one(aa), 0))
    }
  }
}
