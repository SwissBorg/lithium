package com.swissborg.sbr
package reachability

import akka.actor.Address
import akka.cluster.UniqueAddress
import cats.data.NonEmptyMap
import com.swissborg.sbr.testImplicits._
import org.scalatest.{Matchers, WordSpec}

import scala.collection.immutable.SortedMap

class VReachabilitySuite extends WordSpec with Matchers {
  val aa = UniqueAddress(Address("akka.tcp", "sys", "a", 2552), 1L)
  val bb = UniqueAddress(Address("akka.tcp", "sys", "b", 2552), 1L)
  val cc = UniqueAddress(Address("akka.tcp", "sys", "c", 2552), 1L)

  "VReachability" must {
    "not be retrieved when new" in {
      VReachable.notRetrieved.hasBeenRetrieved shouldBe false
      VIndirectlyConnected.fromProtest(aa, bb, 0).hasBeenRetrieved shouldBe false
      VUnreachable.fromDetection(aa, 0).hasBeenRetrieved shouldBe false
    }

    "become retrieved" in {
      VReachable.notRetrieved.tagAsRetrieved.hasBeenRetrieved shouldBe true
      VIndirectlyConnected.fromProtest(aa, bb, 0).tagAsRetrieved.hasBeenRetrieved shouldBe true
      VUnreachable.fromDetection(aa, 0).tagAsRetrieved.hasBeenRetrieved shouldBe true
    }

    "become unreachable from reachable" in {
      VReachable.notRetrieved.withUnreachableFrom(aa, 0) should ===(
        VUnreachable.fromDetection(aa, 0)
      )
    }

    "become unreachable from indirectly-connected" in {
      VIndirectlyConnected.fromProtest(aa, bb, 0).withUnreachableFrom(aa, 0) should ===(
        VUnreachable.fromDetection(aa, 0).withUnreachableFrom(bb, 0)
      )
    }

    "become indirectly-connected from reachable" in {
      VReachable.notRetrieved.withProtest(aa, bb, 0) should ===(
        VIndirectlyConnected.fromProtest(aa, bb, 0)
      )
    }

    "become indirectly-connected from unreachable" in {
      VUnreachable.fromDetection(aa, 0).withProtest(bb, aa, 0) should ===(
        VIndirectlyConnected.fromProtest(bb, aa, 0)
      )

      VUnreachable.fromDetection(aa, 0).withProtest(bb, cc, 0) should ===(
        VIndirectlyConnected.fromProtest(bb, cc, 0)
      )
    }

    "override the older detection when there is a protest with a newer version" in {
      VUnreachable.fromDetection(aa, 0).withProtest(bb, aa, 1) should ===(
        VIndirectlyConnected.fromProtest(bb, aa, 1)
      )
    }

    "override with a newer detection" in {
      VUnreachable.fromDetection(aa, 0).withUnreachableFrom(aa, 1) should ===(
        VUnreachable.fromDetection(aa, 1)
      )
    }

    "override with a newer protest" in {
      VIndirectlyConnected.fromProtest(aa, bb, 0).withProtest(aa, bb, 1) should ===(
        VIndirectlyConnected.fromProtest(aa, bb, 1)
      )

      VIndirectlyConnected.fromProtest(aa, bb, 0).withProtest(cc, bb, 1) should ===(
        VIndirectlyConnected.fromProtest(cc, bb, 1)
      )
    }

    "ignore an older detection" in {
      VUnreachable.fromDetection(aa, 1).withUnreachableFrom(aa, 0) should ===(
        VUnreachable.fromDetection(aa, 1)
      )
    }

    "ignore an older protest" in {
      VIndirectlyConnected.fromProtest(aa, bb, 1).withProtest(aa, bb, 0) should ===(
        VIndirectlyConnected.fromProtest(aa, bb, 1)
      )
    }

    "add a new protester" in {
      VIndirectlyConnected.fromProtest(aa, bb, 0).withProtest(cc, bb, 0) should ===(
        VIndirectlyConnected(
          NonEmptyMap.fromMapUnsafe(
            SortedMap(bb -> DetectionProtest.protested(aa, 0).addProtester(cc, 0))
          )
        )
      )
    }

    "be indirectly-connected after removing part of the protests" in {
      VIndirectlyConnected
        .fromProtest(aa, bb, 0)
        .withProtest(cc, bb, 0)
        .withoutProtest(aa, bb, 0) should ===(VIndirectlyConnected.fromProtest(cc, bb, 0))
    }
  }
}
