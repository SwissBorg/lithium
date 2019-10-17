package akka.cluster.swissborg

import akka.cluster._

sealed abstract class LithiumReachability {
  def observersGroupedByUnreachable: Map[UniqueAddress, Set[UniqueAddress]]

  def allUnreachable: Set[UniqueAddress]

  def allObservers: Set[UniqueAddress]

  def isReachable(node: UniqueAddress): Boolean

  /**
   * Removes the records mentioning any of the `nodes`.
   */
  def remove(nodes: Set[UniqueAddress]): LithiumReachability
}

object LithiumReachability {

  def fromReachability(r: Reachability): LithiumReachability = new LithiumReachability {
    override lazy val observersGroupedByUnreachable: Map[UniqueAddress, Set[UniqueAddress]] =
      r.observersGroupedByUnreachable

    override lazy val allUnreachable: Set[UniqueAddress] = r.allUnreachable

    override lazy val allObservers: Set[UniqueAddress] = r.allObservers

    override def isReachable(node: UniqueAddress): Boolean = r.isReachable(node)

    override def remove(nodes: Set[UniqueAddress]): LithiumReachability = fromReachability(r.remove(nodes))
  }

  // Used for testing
  def apply(reachableNodes: Set[UniqueAddress],
            observersGroupedByUnreachable0: Map[UniqueAddress, Set[UniqueAddress]]): LithiumReachability =
    new LithiumReachability {
      override val observersGroupedByUnreachable: Map[UniqueAddress, Set[UniqueAddress]] =
        observersGroupedByUnreachable0

      override lazy val allUnreachable: Set[UniqueAddress] = observersGroupedByUnreachable.keySet

      override lazy val allObservers: Set[UniqueAddress] = observersGroupedByUnreachable.values.flatten.toSet

      override def isReachable(node: UniqueAddress): Boolean = reachableNodes.contains(node)

      override def remove(nodes: Set[UniqueAddress]): LithiumReachability =
        LithiumReachability(
          reachableNodes.diff(nodes),
          observersGroupedByUnreachable.flatMap {
            case (unreachable, observers) =>
              if (nodes.contains(unreachable)) None
              else {
                val updateObservers = observers -- nodes
                if (updateObservers.isEmpty) None
                else Some((unreachable, updateObservers))
              }
          }
        )
    }
}
