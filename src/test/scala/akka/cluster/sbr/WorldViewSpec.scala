package akka.cluster.sbr

import akka.cluster.ClusterEvent._
import akka.cluster.sbr.ArbitraryInstances._
import akka.cluster.sbr.WorldView._
import akka.cluster.sbr.implicits._
import cats.kernel.Eq

class WorldViewSpec extends MySpec {
  "WorldView" - {
    "1 - should not have a node simultaneously reachable and unreachable" in {
      forAll { worldView: WorldView =>
        worldView.reachableConsideredNodes.map(_.node).intersect(worldView.unreachableNodes.map(_.node)) shouldBe empty
      }
    }

    "2 - memberEvent" in {
      forAll { (worldView: WorldView, event: MemberEvent) =>
        event match {
          case MemberJoined(node) =>
            worldView.memberEvent(event) match {
              case Right(w) =>
                if (worldView.self !== node) {
                  w.statusOf(node) should ===(Some(Staged))
                  w.otherStatuses.keySet.contains(node) shouldBe true
                  w.reachableConsideredNodes.contains(ReachableConsideredNode(node)) shouldBe false
                  w.unreachableNodes.contains(UnreachableNode(node)) shouldBe false
                } else {
                  w should ===(worldView)
                }

              case Left(err) =>
                err match {
                  case IllegalTransition(errMember, _, Staged) =>
                    worldView.statusOf(event.member) should not be empty
                    worldView.self should !==(node)
                    node should ===(errMember)

                  case _ => fail
                }
            }

          case MemberWeaklyUp(node) =>
            worldView.memberEvent(event) match {
              case Right(w) =>
                w.statusOf(node) should ===(Some(WeaklyReachable))
                w.otherStatuses.keySet.contains(node) shouldBe true
                w.reachableConsideredNodes.contains(ReachableConsideredNode(node)) shouldBe false
                w.unreachableNodes.contains(UnreachableNode(node)) shouldBe false

              case Left(err) =>
                err.node should ===(node)

                err match {
                  case IllegalTransition(errNode, from, WeaklyReachable) =>
                    worldView.statusOf(errNode) should ===(Some(from))

                  case IllegalUnreachable(errNode) =>
                    worldView.self should ===(errNode)
                    worldView.statusOf(errNode) should ===(Some(Unreachable))

                  case UnknownNode(errNode) =>
                    worldView.statusOf(errNode) shouldBe empty
                    worldView.self should !==(errNode)

                  case _ => fail
                }
            }

          case _: MemberLeft | _: MemberExited =>
            worldView.memberEvent(event) match {
              case Right(w) => w should ===(worldView)

              case Left(UnknownNode(node)) =>
                node should ===(event.member)
                worldView.statusOf(node).isEmpty shouldBe true

              case _ => fail
            }

          case MemberDowned(member) =>
            worldView.memberEvent(event) match {
              case Right(w)                => w should ===(worldView)
              case Left(UnknownNode(node)) => node should ===(member)
              case _                       => fail
            }

          case MemberRemoved(member, _) =>
            worldView.memberEvent(event) match {
              case Right(w) =>
                w.reachableConsideredNodes.contains(ReachableConsideredNode(member)) shouldBe false
                w.unreachableNodes.contains(UnreachableNode(member)) shouldBe false

              case Left(CannotRemoveSelf(node)) =>
                node should ===(member)
                node should ===(worldView.self)

              case Left(UnknownNode(node)) =>
                node should ===(member)
                worldView.statusOf(node) shouldBe empty
                event.member should !==(worldView.self)

              case other => fail(s"$other")
            }

          case MemberUp(member) =>
            worldView.memberEvent(event) match {
              case Right(w) =>
                w.reachableConsideredNodes.contains(ReachableConsideredNode(member)) shouldBe true
                w.unreachableNodes.contains(UnreachableNode(member)) shouldBe false

              case Left(IllegalTransition(node, _, Reachable)) =>
                node should ===(member)
                event.member should !==(worldView.self)

              case other => fail(s"$other")
            }
        }
      }
    }

    "3 - reachabilityEvent" in {
      forAll { (worldView: WorldView, event: ReachabilityEvent) =>
        event match {
          case UnreachableMember(node) =>
            worldView.reachabilityEvent(event) match {
              case Right(w) =>
                w.statusOf(node) should ===(Some(Unreachable))

              case Left(IllegalUnreachable(node)) =>
                event.member should ===(node)
                event.member should ===(worldView.self)

              case Left(UnknownNode(node)) =>
                event.member should ===(node)
                event.member should !==(worldView.self)
                worldView.allStatuses.contains(event.member) shouldBe false

              case _ => fail
            }

          case ReachableMember(node) =>
            worldView.reachabilityEvent(event) match {
              case Right(w) =>
                w.statusOf(node) should ===(Some(Reachable))

              case Left(err) =>
                event.member should ===(err.node)
                err match {
                  case UnknownNode(node)                     => worldView.allStatuses.contains(node) shouldBe false
                  case IllegalTransition(node, _, Reachable) => worldView.statusOf(node).isDefined shouldBe true
                  case _                                     => fail
                }
            }

        }
      }
    }

    "4 - reachableConsideredNodes" in {
      forAll { worldView: WorldView =>
        assert(
          worldView.reachableConsideredNodes
            .forall(n => worldView.allStatuses.lookup(n.node).exists(Eq[Status].eqv(_, Reachable)))
        )
      }
    }

    "5 - unreachableNodes" in {
      forAll { worldView: WorldView =>
        assert(
          worldView.unreachableNodes.forall(
            n => worldView.allStatuses.lookup(n.node).exists(Eq[Status].eqv(_, Unreachable))
          )
        )
      }
    }

    "6 - allNodes" in {
      forAll { worldView: WorldView =>
        assert(
          worldView.allConsideredNodes
            .forall((worldView.reachableConsideredNodes.map(_.node) ++ worldView.unreachableNodes.map(_.node)).contains)
        )
      }
    }

    "7 - allNodesWithRole" in {
      forAll { (worldView: WorldView, role: String) =>
        if (role == "") worldView.allConsideredNodesWithRole(role) should ===(worldView.allConsideredNodes)
        else assert(worldView.allConsideredNodesWithRole(role).forall(worldView.allConsideredNodes.contains))

        worldView.allConsideredNodesWithRole(role) should ===(
          worldView
            .reachableConsideredNodesWithRole(role)
            .map(_.node) ++ worldView
            .unreachableNodesWithRole(role)
            .map(_.node)
        )
      }
    }

    "8 - reachableNodesWithRole" in {
      forAll { (worldView: WorldView, role: String) =>
        if (role == "") worldView.reachableConsideredNodesWithRole(role) should ===(worldView.reachableConsideredNodes)
        else
          assert(
            worldView
              .reachableConsideredNodesWithRole(role)
              .forall(worldView.reachableConsideredNodes.contains)
          )
      }
    }

    "9 - unreachableNodesWithRole" in {
      forAll { (worldView: WorldView, role: String) =>
        if (role == "") worldView.unreachableNodesWithRole(role) should ===(worldView.unreachableNodes)
        else assert(worldView.unreachableNodesWithRole(role).forall(worldView.unreachableNodes.contains))
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
        worldView.reachableConsideredNodes shouldBe 'nonEmpty
      }
    }
  }
}
