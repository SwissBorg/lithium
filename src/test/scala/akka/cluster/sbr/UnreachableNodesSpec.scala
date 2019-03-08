package akka.cluster.sbr

import akka.cluster.sbr.ArbitraryInstances._
import eu.timepit.refined.auto._
import org.scalacheck.Prop._
import org.scalacheck.Properties

class UnreachableNodesSpec extends Properties("UnreachableNodes") {
  property("unreachableNodes") = forAll { (reachability: Reachability, quorumSize: QuorumSize) =>
    UnreachableNodes(reachability, quorumSize) match {
      case EmptyUnreachable() =>
        reachability.unreachableNodes.isEmpty

      case UnreachablePotentialQuorum(unreachableNodes) =>
        unreachableNodes.length >= quorumSize &&
          unreachableNodes.toSortedSet.diff(reachability.unreachableNodes).isEmpty

      case UnreachableSubQuorum(unreachableNodes) =>
        unreachableNodes.length < quorumSize &&
          unreachableNodes.toSortedSet.diff(reachability.unreachableNodes).isEmpty
    }
  }
}
