package com.swissborg.sbr.reachability

import akka.actor.Address
import akka.cluster.UniqueAddress
import cats.data._
import com.swissborg.sbr.testImplicits._
import com.swissborg.sbr.reachability.DetectionProtest._
import org.scalatest.{Matchers, WordSpec}

class DetectionProtestSuite extends WordSpec with Matchers {
  val aa = UniqueAddress(Address("akka.tcp", "sys", "a", 2552), 1L)
  val bb = UniqueAddress(Address("akka.tcp", "sys", "b", 2552), 1L)

  "DetectionProtest" must {
    "become protested" in {
      val unprotested = Unprotested(0)
      unprotested.addProtester(aa, 0) should ===(Protested(NonEmptySet.one(aa), 0))
    }

    "stay unprotested" in {
      val unprotested = Unprotested(1)
      unprotested.removeProtester(aa, 1) should ===(unprotested)
      unprotested.addProtester(aa, 0) should ===(unprotested)
    }

    "become unprotested" in {
      val protested = Protested.one(aa, 0)
      protested.removeProtester(aa, 0) should ===(Unprotested(0))
      protested.detection(1) should ===(Unprotested(1))
    }

    "stay protested" in {
      val protested = Protested.one(aa, 1).addProtester(bb, 1)
      protested.removeProtester(bb, 1) should ===(Protested(NonEmptySet.one(aa), 1))

      protested.removeProtester(bb, 1).addProtester(bb, 0) should ===(
        Protested(NonEmptySet.one(aa), 1)
      )
    }
  }
}
