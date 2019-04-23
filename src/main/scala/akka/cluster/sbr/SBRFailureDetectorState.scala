package akka.cluster.sbr

import akka.cluster.{HeartbeatNodeRing, Member, UniqueAddress}
import akka.cluster.sbr.SBRFailureDetector.SBRReachability

/**
 * State of the SBRFailureDetector.
 *
 * Keeps the `ring` in sync with the one in `ClusterHeartBeatSender`
 * to known who the current observers are of specific nodes.
 */
final private[sbr] case class SBRFailureDetectorState(ring: HeartbeatNodeRing,
                                                      lastReachabilities: Map[UniqueAddress, SBRReachability],
                                                      selfMember: Member) {
  def add(m: Member): SBRFailureDetectorState = {
    val node = m.uniqueAddress
    if (node != selfMember.uniqueAddress && !ring.nodes(node) && selfMember.dataCenter == m.dataCenter) {
      copy(ring = ring :+ node)
    } else {
      this
    }
  }

  def remove(m: Member): SBRFailureDetectorState = {
    val lastReachabilities0 = lastReachabilities - m.uniqueAddress

    if (m.dataCenter == selfMember.dataCenter) {
      copy(ring = ring :- m.uniqueAddress, lastReachabilities = lastReachabilities0)
    } else {
      copy(lastReachabilities = lastReachabilities0)
    }
  }

  def reachable(m: Member): SBRFailureDetectorState =
    copy(ring = ring.copy(unreachable = ring.unreachable - m.uniqueAddress))

  def unreachable(m: Member): SBRFailureDetectorState =
    copy(ring = ring.copy(unreachable = ring.unreachable + m.uniqueAddress))

  def update(member: Member, reachability: SBRReachability): SBRFailureDetectorState =
    copy(lastReachabilities = lastReachabilities + (member.uniqueAddress -> reachability))
}

private[sbr] object SBRFailureDetectorState {
  def apply(selfMember: Member, monitoredByNrOfMembers: Int): SBRFailureDetectorState =
    SBRFailureDetectorState(
      HeartbeatNodeRing(selfMember.uniqueAddress, Set(selfMember.uniqueAddress), Set.empty, monitoredByNrOfMembers),
      Map.empty,
      selfMember
    )
}
