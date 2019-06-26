package com.swissborg.sbr
package reachability

import akka.cluster.UniqueAddress
import cats.data.State

/**
  * State of the SBRFailureDetector.
  */
final private[reachability] case class SBReachabilityReporterState private (
    selfUniqueAddress: UniqueAddress,
    reachabilities: Map[Subject, VReachability],
    pendingContentionAcks: Map[UniqueAddress, Set[SBReachabilityReporter.ContentionAck]],
    receivedAcks: Map[UniqueAddress, Set[SBReachabilityReporter.ContentionAck]]
) {

  /**
    * Set the `subject` as reachable.
    */
  private[reachability] def withReachable(subject: Subject): SBReachabilityReporterState =
    _withReachable(subject, tagAsRetrieved = false)

  /**
    * Set the `subject` as reachable and already tag its reachability as retrieved
    */
  private[reachability] def withReachableAndSetAsRetrieved(
      subject: Subject
  ): SBReachabilityReporterState =
    _withReachable(subject, tagAsRetrieved = true)

  /**
    * Set the `subject` as reachable.
    */
  private def _withReachable(
      subject: Subject,
      tagAsRetrieved: Boolean
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
  private[reachability] def withUnreachableFrom(
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
  private[reachability] def withContention(
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
  private[reachability] def withoutContention(
      protester: Protester,
      observer: Observer,
      subject: Subject,
      version: Version
  ): SBReachabilityReporterState = {
    val updatedReachabilities = reachabilities + (subject -> reachabilities
      .get(subject)
      .fold(VReachable.notRetrieved)(_.withoutProtest(protester, observer, version)))

    copy(reachabilities = updatedReachabilities)
  }

  /**
    * Remove the node.
    */
  private[reachability] def remove(node: UniqueAddress): SBReachabilityReporterState =
    copy(
      reachabilities = (reachabilities - node).map {
        case (subject, reachability) => subject -> reachability.remove(node)
      },
      pendingContentionAcks = pendingContentionAcks - node,
      receivedAcks = receivedAcks - node
    )

  private[reachability] def expectContentionAck(
      ack: SBReachabilityReporter.ContentionAck
  ): SBReachabilityReporterState =
    copy(
      pendingContentionAcks = pendingContentionAcks + (ack.from -> (pendingContentionAcks
        .getOrElse(ack.from, Set.empty) + ack))
    )

  private[reachability] def registerContentionAck(
      ack: SBReachabilityReporter.ContentionAck
  ): SBReachabilityReporterState = {
    val updatedPendingContentionAcks = pendingContentionAcks
      .get(ack.from)
      .fold(pendingContentionAcks) { pendingAcks =>
        val newPendingAcks = pendingAcks - ack

        if (newPendingAcks.isEmpty) pendingContentionAcks - ack.from
        else pendingContentionAcks + (ack.from -> newPendingAcks)
      }

    val updatedReceivedAcks = receivedAcks + (ack.from -> (receivedAcks.getOrElse(
      ack.from,
      Set.empty
    ) + ack))

    copy(pendingContentionAcks = updatedPendingContentionAcks, receivedAcks = updatedReceivedAcks)
  }
}

private[reachability] object SBReachabilityReporterState {
  def apply(selfUniqueAddress: UniqueAddress): SBReachabilityReporterState =
    SBReachabilityReporterState(selfUniqueAddress, Map.empty, Map.empty, Map.empty)

  /**
    * Return the `subject`'s reachability if it has changed since the last time
    * this method was called.
    *
    * The status is `None` when it has not changed since the last reachability retrieval.
    */
  private[reachability] def updatedReachability(
      subject: Subject
  ): State[SBReachabilityReporterState, Option[SBReachabilityStatus]] = State { s =>
    s.reachabilities
      .get(subject)
      .fold(
        (
          s.withReachableAndSetAsRetrieved(subject),
          Option[SBReachabilityStatus](SBReachabilityStatus.Reachable)
        )
      ) { reachability =>
        if (reachability.hasBeenRetrieved) {
          (s, None)
        } else {
          (
            s.copy(reachabilities = s.reachabilities + (subject -> reachability.tagAsRetrieved)),
            Some(reachability.toSBReachabilityStatus)
          )
        }
      }
  }

  /**
    * Return all the changed reachabilities since the last time this method was called.
    */
  private[reachability] val allUpdatedReachabilies
      : State[SBReachabilityReporterState, Map[Subject, SBReachabilityStatus]] = State { s =>
    s.reachabilities
      .foldLeft((s, Map.empty[Subject, SBReachabilityStatus])) {
        case ((s, statuses), (subject, reachability)) =>
          if (reachability.hasBeenRetrieved) {
            (s, statuses)
          } else {
            (
              s.copy(reachabilities = s.reachabilities + (subject -> reachability.tagAsRetrieved)),
              statuses + (subject -> reachability.toSBReachabilityStatus)
            )
          }
      }
  }
}
