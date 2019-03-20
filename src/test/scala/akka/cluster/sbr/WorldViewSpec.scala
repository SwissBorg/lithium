package akka.cluster.sbr

import akka.cluster.ClusterEvent._
import akka.cluster.sbr.ArbitraryInstances._
import akka.cluster.sbr.WorldView._

class WorldViewSpec extends MySpec {
  "WorldView" - {
    "1 - should not have a node simultaneously reachable and unreachable" in {
      forAll { worldView: WorldView =>
        worldView.reachableNodes.map(_.node).intersect(worldView.unreachableNodes.map(_.node)) shouldBe empty
      }
    }

    "2 - memberEvent" in {
      forAll { (worldView: WorldView, event: MemberEvent) =>
        event match {
          case MemberJoined(member) =>
            worldView.memberEvent(event) match {
              case Right(w) =>
                if (worldView.self != member) {
                  w.statuses.keys should contain(member)
                  w.reachableNodes shouldNot contain(ReachableNode(member))
                  w.unreachableNodes shouldNot contain(UnreachableNode(member))
                } else {
                  w shouldEqual worldView
                }

              case Left(err) =>
                err match {
                  case NodeAlreadyJoined(errMember) =>
                    (worldView.self should not).equal(member)
                    member shouldEqual errMember

                  case SelfShouldExist(errMember) =>
                    worldView.self shouldEqual member
                    member shouldEqual errMember

                  case _ =>
                    fail
                }
            }

          case MemberWeaklyUp(member) =>
            val worldView0 = worldView.memberEvent(event) match {
              case Right(w) =>
                w.statuses.keys should contain(member)
                w.reachableNodes shouldNot contain(ReachableNode(member))
                w.unreachableNodes shouldNot contain(UnreachableNode(member))

              case Left(err) => err shouldEqual NodeAlreadyCounted(member)
            }

          case _: MemberDowned | _: MemberLeft | _: MemberExited =>
            worldView.memberEvent(event) match {
              case Right(w) => w shouldEqual worldView
              case Left(err) =>
                worldView.statusOf(event.member) match {
                  case Some(Staged) => err shouldEqual NodeStillStaged(event.member)
                  case None         => err shouldEqual UnknownNode(event.member)
                  case _            => fail
                }
            }

          case MemberRemoved(member, _) =>
            worldView.memberEvent(event) match {
              case Right(w) =>
                w.reachableNodes shouldNot contain(ReachableNode(member))
                w.unreachableNodes shouldNot contain(UnreachableNode(member))

              case Left(err) => err shouldEqual UnknownNode(member)
            }

          case MemberUp(member) =>
            worldView.memberEvent(event) match {
              case Right(w) =>
                w.reachableNodes should contain(ReachableNode(member))
                w.unreachableNodes shouldNot contain(UnreachableNode(member))

              case Left(err) => err shouldEqual NodeAlreadyCounted(member)
            }
        }
      }
    }

    "3 - reachabilityEvent" in {
      forAll { (worldView: WorldView, event: ReachabilityEvent) =>
        event match {
          case UnreachableMember(member) =>
            val worldView0 = worldView.copy(statuses = worldView.statuses + (member -> Reachable))
            val worldView1 = worldView0.reachabilityEvent(event).toTry.get
            worldView1.reachableNodes shouldNot contain(ReachableNode(member))
            worldView1.unreachableNodes should contain(UnreachableNode(member))

          case ReachableMember(member) =>
            val worldView0 = worldView.copy(statuses = worldView.statuses + (member -> Unreachable))
            val worldView1 = worldView0.reachabilityEvent(event).toTry.get
            worldView1.reachableNodes should contain(ReachableNode(member))
            worldView1.unreachableNodes shouldNot contain(UnreachableNode(member))
        }
      }
    }

    "4 - reachableNodes" in {
      forAll { worldView: WorldView =>
        (worldView.statuses.toList should contain)
          .allElementsOf(worldView.reachableNodes.map(n => n.node -> Reachable))
      }
    }

    "5 - unreachableNodes" in {
      forAll { worldView: WorldView =>
        (worldView.statuses.toList should contain)
          .allElementsOf(worldView.unreachableNodes.map(n => n.node -> Unreachable))
      }
    }

    "6 - allNodes" in {
      forAll { worldView: WorldView =>
        (worldView.allNodes should contain)
          .theSameElementsAs(worldView.reachableNodes.map(_.node) ++ worldView.unreachableNodes.map(_.node))
      }
    }

    "7 - allNodesWithRole" in {
      forAll { (worldView: WorldView, role: String) =>
        if (role == "") worldView.allNodesWithRole(role) shouldEqual worldView.allNodes
        else (worldView.allNodes should contain).allElementsOf(worldView.allNodesWithRole(role))

        worldView.allNodesWithRole(role) shouldEqual (worldView.reachableNodesWithRole(role).map(_.node) ++ worldView
          .unreachableNodesWithRole(role)
          .map(_.node))
      }
    }

    "8 - reachableNodesWithRole" in {
      forAll { (worldView: WorldView, role: String) =>
        if (role == "") worldView.reachableNodesWithRole(role) shouldEqual worldView.reachableNodes
        else (worldView.reachableNodes should contain).allElementsOf(worldView.reachableNodesWithRole(role))
      }
    }

    "9 - unreachableNodesWithRole" in {
      forAll { (worldView: WorldView, role: String) =>
        if (role == "") worldView.unreachableNodesWithRole(role) shouldEqual worldView.unreachableNodes
        else (worldView.unreachableNodes should contain).allElementsOf(worldView.unreachableNodesWithRole(role))
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
