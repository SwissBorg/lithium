package com.swissborg.sbr.reachability

import akka.cluster.UniqueAddress
import com.swissborg.sbr.reachability.SBReachabilityReporter._
import com.swissborg.sbr.reachability.SBReachabilityReporterState._

/**
  * State of the SBRFailureDetector.
  */
final private[sbr] case class SBReachabilityReporterState private (
    selfUniqueAddress: UniqueAddress,
    reachabilities: Map[Subject, VReachability],
    pendingContentionAcks: Map[UniqueAddress, Set[ContentionAck]],
    receivedAcks: Map[UniqueAddress, ContentionAck]
) {

  /**
    * Return the `subject`'s status if it has changed since the last time
    * this method was called.
    *
    * The status is `None` when it has not changed since the last status retrieval.
    */
  def updatedStatus(subject: Subject): (Option[SBReachabilityStatus], SBReachabilityReporterState) =
    reachabilities
      .get(subject)
      .map {
        case r @ UpdatedReachability(reachability) =>
          (
            Some(reachability),
            copy(reachabilities = reachabilities + (subject -> r.tagAsRetrieved))
          )
        case _ => (None, this)
      }
      .getOrElse(
        (Some(SBReachabilityStatus.Reachable), withReachable(subject, tagAsRetrieved = true))
      )

  /**
    * Set the `subject` as reachable.
    */
  def withReachable(
      subject: Subject,
      tagAsRetrieved: Boolean = false
  ): SBReachabilityReporterState = {
    val reachable = VReachable.notRetrieved
    copy(
      reachabilities = reachabilities + (subject -> (if (tagAsRetrieved) reachable.tagAsRetrieved
                                                     else reachable))
    )
  }

  /**
    * Set the `subject` as unreachable from the `observer`.
    * The version must be non-decreasing for each `observer`-`subject` pair.
    */
  def withUnreachableFrom(
      observer: Observer,
      subject: Subject,
      version: Version
  ): SBReachabilityReporterState = {
    val updatedReachability = reachabilities
      .get(subject)
      .fold(VReachability.unreachableFrom(observer, version))(
        _.withUnreachableFrom(observer, version)
      )

    copy(reachabilities = reachabilities + (subject -> updatedReachability))
  }

  /**
    * Update the contention of the observation of `subject` by `observer`
    * by the cluster node `node`.
    */
  def withContention(
      protester: Protester,
      observer: Observer,
      subject: Subject,
      version: Version
  ): SBReachabilityReporterState =
    copy(
      reachabilities = reachabilities + (subject -> reachabilities
        .get(subject)
        .fold(VIndirectlyConnected.fromProtest(protester, observer, version))(
          _.withProtest(protester, observer, version)
        ))
    )

  // TODO need version?
  def withoutContention(
      protester: Protester,
      observer: Observer,
      subject: Subject
  ): SBReachabilityReporterState = {
    val updatedReachabilities = reachabilities + (subject -> reachabilities
      .get(subject)
      .fold(VReachable.notRetrieved)(_.withoutProtest(protester, observer)))

    copy(reachabilities = updatedReachabilities)
  }

  /**
    * Remove the node.
    */
  def remove(node: UniqueAddress): SBReachabilityReporterState =
    copy(
      reachabilities = (reachabilities - node).map {
        case (subject, reachability) => subject -> reachability.remove(node)
      },
      pendingContentionAcks = pendingContentionAcks - node,
      receivedAcks = receivedAcks - node
    )

  def expectContentionAck(ack: ContentionAck): SBReachabilityReporterState =
    copy(
      pendingContentionAcks = pendingContentionAcks + (ack.from -> (pendingContentionAcks
        .getOrElse(ack.from, Set.empty) + ack))
    )

  def registerContentionAck(ack: ContentionAck): SBReachabilityReporterState =
    pendingContentionAcks.get(ack.from).fold(this) { pendingAcks =>
      val newPendingAcks = pendingAcks - ack

      val newPendingContentionAcks =
        if (newPendingAcks.isEmpty) pendingContentionAcks - ack.from
        else pendingContentionAcks + (ack.from -> newPendingAcks)

      copy(
        pendingContentionAcks = newPendingContentionAcks,
        receivedAcks = receivedAcks + (ack.from -> ack)
      )
    }
}

object SBReachabilityReporterState {
  def apply(selfUniqueAddress: UniqueAddress): SBReachabilityReporterState =
    SBReachabilityReporterState(selfUniqueAddress, Map.empty, Map.empty, Map.empty)

  object UpdatedReachability {
    def unapply(arg: VReachability): Option[SBReachabilityStatus] =
      if (!arg.hasBeenRetrieved) {
        Some(arg match {
          case _: VReachable           => SBReachabilityStatus.Reachable
          case _: VIndirectlyConnected => SBReachabilityStatus.IndirectlyConnected
          case _: VUnreachable         => SBReachabilityStatus.Unreachable
        })
      } else {
        None
      }
  }
}
