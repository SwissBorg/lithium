package akka.cluster.sbr

import akka.cluster.Member
import akka.cluster.sbr.ArbitraryInstances._
import akka.cluster.sbr.SBRFailureDetector.SBRReachability

class SBRFailureDetectorStateSpec extends MySpec {
  "SBRFailureDetectorState" - {
    "1 - Add members" in {
      forAll { (s: SBRFailureDetectorState, m: Member) =>
        val s0 = s.add(m)

        if (m.uniqueAddress == s.selfMember.uniqueAddress) {
          s0.ring.nodes(m.uniqueAddress) shouldBe true
        } else if (s.selfMember.dataCenter != m.dataCenter) {
          s0.ring.nodes(m.uniqueAddress) shouldBe false
        } else {
          s0.ring.nodes(m.uniqueAddress) shouldBe true
        }
      }
    }

    "2 - Remove members" in {
      forAll { (s: SBRFailureDetectorState, m: Member) =>
        if (m.uniqueAddress != s.selfMember.uniqueAddress) {
          val s0 = s.remove(m)
          s0.ring.nodes(m.uniqueAddress) shouldBe false
          s0.lastReachabilities.contains(m.uniqueAddress) shouldBe false
        }
      }
    }

    "3 - Unreachable members" in {
      forAll { (s: SBRFailureDetectorState, m: Member) =>
        val s0 = s.unreachable(m)
        s0.ring.unreachable(m.uniqueAddress) shouldBe true
      }
    }

    "4 - Reachable members" in {
      forAll { (s: SBRFailureDetectorState, m: Member) =>
        val s0 = s.reachable(m)
        s0.ring.unreachable(m.uniqueAddress) shouldBe false
      }
    }

    "5 - Update" in {
      forAll { (s: SBRFailureDetectorState, m: Member, r: SBRReachability) =>
        val s0 = s.update(m, r)
        s0.lastReachabilities.get(m.uniqueAddress) shouldEqual Some(r)
      }
    }
  }
}
