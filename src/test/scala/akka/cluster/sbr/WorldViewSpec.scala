package akka.cluster.sbr

import akka.cluster.ClusterEvent._
import akka.cluster.sbr.ArbitraryInstances._
import org.scalatest.prop.PropertyChecks
import org.scalatest.{FreeSpec, Matchers}

class WorldViewSpec extends FreeSpec with Matchers with PropertyChecks {
  "WorldView" - {
    "1 - should not have a node simultaneously reachable and unreachable" in {
      forAll { (worldView: WorldView, memberEvents: List[MemberEvent], reachabilityEvent: List[ReachabilityEvent]) =>
        val worldView0 = memberEvents.foldLeft(worldView) {
          case (worldView, event) => worldView.memberEvent(event)
        }

        val worldView1 = reachabilityEvent.foldLeft(worldView0) {
          case (worldView, event) => worldView.reachabilityEvent(event)
        }

        worldView1.reachableNodes.map(_.node).intersect(worldView1.unreachableNodes.map(_.node)) shouldBe empty
      }
    }

    "2 - memberEvent" in {
      forAll { (worldView: WorldView, event: MemberEvent) =>
        event match {
          case MemberWeaklyUp(_) =>
            worldView.memberEvent(event) shouldEqual worldView

          case MemberRemoved(member, _) =>
            val worldView0 = worldView.memberEvent(event)
            worldView0.reachableNodes shouldNot contain(ReachableNode(member))
            worldView0.unreachableNodes shouldNot contain(UnreachableNode(member))

          case _ =>
            val worldView0 = worldView.memberEvent(event)
            worldView0.reachableNodes should contain(ReachableNode(event.member))
            worldView0.unreachableNodes shouldNot contain(UnreachableNode(event.member))
        }
      }
    }

    "3 - reachabilityEvent" in {
      forAll { (worldView: WorldView, event: ReachabilityEvent) =>
        event match {
          case UnreachableMember(member) =>
            val worldView0 = worldView.reachabilityEvent(event)
            worldView0.reachableNodes shouldNot contain(ReachableNode(member))
            worldView0.unreachableNodes should contain(UnreachableNode(member))

          case ReachableMember(member) =>
            val worldView0 = worldView.reachabilityEvent(event)
            worldView0.reachableNodes should contain(ReachableNode(member))
            worldView0.unreachableNodes shouldNot contain(UnreachableNode(member))
        }
      }
    }

    "4 - reachableNodes" in {
      forAll { worldView: WorldView =>
        (worldView.m.toList should contain).allElementsOf(worldView.reachableNodes.map(n => n.node -> Reachable))
      }
    }

    "5 - unreachableNodes" in {
      forAll { worldView: WorldView =>
        (worldView.m.toList should contain).allElementsOf(worldView.unreachableNodes.map(n => n.node -> Unreachable))
      }
    }
  }

  "HealthyWorldView" - {
    "1 - should not have unreachable nodes" in {
      forAll { worldView: HealthyWorldView =>
        worldView.unreachableNodes shouldBe empty
      }
    }

    "2 - should have at least a reachable node" in {
      forAll { worldView: HealthyWorldView =>
        worldView.reachableNodes shouldBe 'nonEmpty
      }

    }
  }
}
