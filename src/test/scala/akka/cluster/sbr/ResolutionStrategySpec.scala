package akka.cluster.sbr

import org.scalacheck.Prop._
import org.scalacheck.{Prop, Properties}
import akka.cluster.sbr.ArbitraryInstances._

class ResolutionStrategySpec extends Properties("ResolutionStrategy") {
  import ResolutionStrategySpec._

  property("staticQuorum") = forAll {
    (maybeReachableNodes: Either[NoReachableNodesError.type, ReachableNodes], unreachableNodes: UnreachableNodes) =>
      maybeReachableNodes.isRight ==> {
        val reachableNodes = maybeReachableNodes.right.get

        val strategy = ResolutionStrategy.staticQuorum(reachableNodes, unreachableNodes)

        classifyNetwork(reachableNodes, unreachableNodes) {
          strategy match {
            case DownReachable(nodeGroup) =>
              reachableNodes match {
                case ReachableQuorum(reachableNodes)    => false
                case ReachableSubQuorum(reachableNodes) => true
              }

            case DownUnreachable(nodeGroup) =>
              reachableNodes match {
                case ReachableQuorum(reachableNodes)    => true
                case ReachableSubQuorum(reachableNodes) => false
              }

            case UnsafeDownReachable(nodeGroup) =>
              (reachableNodes, unreachableNodes) match {
                case (_: ReachableQuorum, _: UnreachablePotentialQuorum) => true
                case _                                                   => false
              }

            case Idle() =>
              unreachableNodes match {
                case EmptyUnreachable() => true
                case _                  => false
              }
          }
        }
      }
  }

  property("bla") = forAll { splitScenario: SplitScenario =>
    true
  }
}

object ResolutionStrategySpec {
  def classifyNetwork(reachableNodes: ReachableNodes, unreachableNodes: UnreachableNodes)(prop: Prop): Prop = {
    val isNormal: Boolean =
      (reachableNodes, unreachableNodes) match {
        case (_, _: EmptyUnreachable) => true
        case _                        => false
      }

    val reachableIsQuorum: Boolean = reachableNodes match {
      case _: ReachableQuorum    => true
      case _: ReachableSubQuorum => false
    }

    val unreachableIsPotentialQuorum: Boolean = unreachableNodes match {
      case _: UnreachablePotentialQuorum => true
      case _                             => false
    }

    classify(isNormal, "normal", "split-brain") {
      classify(reachableIsQuorum, "reachable-is-quorum", "reachable-is-not-quorum") {
        classify(unreachableIsPotentialQuorum, "unreachable-is-maybe-quorum", "unreachable-is-not-quorum") {
          prop
        }
      }
    }
  }
}
