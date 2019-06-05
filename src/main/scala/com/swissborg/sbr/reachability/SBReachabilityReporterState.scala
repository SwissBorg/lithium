package com.swissborg.sbr.reachability

import akka.actor.ActorPath
import akka.cluster.UniqueAddress
import cats.Monoid
import cats.implicits._
import com.swissborg.sbr.Util.pathAtAddress
import com.swissborg.sbr.reachability.SBReachabilityReporter._
import com.swissborg.sbr.reachability.SBReachabilityReporterState._

/**
 * State of the SBRFailureDetector.
 */
final private[sbr] case class SBReachabilityReporterState private (
  selfPath: ActorPath,
  reachabilities: Map[Subject, VersionedReachability],
  contentions: Map[Subject, Map[Observer, ContentionAggregator]],
  pendingContentionAcks: Map[ActorPath, Set[ContentionAck]],
  pendingIntroductionAcks: Map[ActorPath, IntroductionAck]
) {

  /**
   * Return the `subject`'s status if it has changed since the last time
   * this method was called.
   *
   * The status is `None` when it has not changed since the last status retrieval.
   */
  def updatedStatus(subject: Subject): (Option[SBRReachabilityStatus], SBReachabilityReporterState) =
    reachabilities
      .get(subject)
      .map { r =>
        if (!r.retrieved) {
          // The reachability has changed since the last retrieval
          (Some(r.reachability), copy(reachabilities = reachabilities + (subject -> r.tagAsRetrieved)))
        } else {
          (None, this)
        }
      }
      .getOrElse {
        // The node is seen for the 1st time.
        // It is reachable by default.
        val r = withReachable(subject)
        (Some(Reachable),
         r.copy(reachabilities = r.reachabilities + (subject -> r.reachabilities(subject).tagAsRetrieved)))
      }

  /**
   * Set the `subject` as reachable.
   */
  def withReachable(subject: Subject): SBReachabilityReporterState =
    copy(reachabilities = reachabilities + (subject -> updateReachability(subject, Reachable)),
         contentions = contentions - subject)

  /**
   * Set `node` as unreachable and removes the current node
   * from all the related contentions.
   */
  def withUnreachableFrom(observer: Observer, subject: Subject): SBReachabilityReporterState = {
    // The observer node now agrees.
    val updatedContentions =
      contentions
        .get(subject)
        .map { cs =>
          contentions + (subject -> cs.flatMap {
            case (o, c) =>
              if (c.disagreeing.size === 1 && c.disagreeing(observer)) {
                None // No one disagrees after removing the `observer`.
              } else {
                Some(o -> c.agree(observer))
              }
          })
        }
        .getOrElse(contentions) // nothing to do as there's no ongoing contention

    // The node is being tagged as unreachable. So if no contention exists for it we can
    // safely assume it is unreachable. It is either unreachable or indirectly connected.
    val isUnreachable = updatedContentions.get(subject).forall(_.values.forall(_.disagreeing.isEmpty))

    if (isUnreachable) {
      copy(reachabilities = reachabilities + (subject -> updateReachability(subject, Unreachable)),
           contentions = updatedContentions - subject)
    } else {
      copy(reachabilities = reachabilities + (subject -> updateReachability(subject, IndirectlyConnected)),
           contentions = updatedContentions)
    }
  }

  /**
   * Update the reachability of `subject` to `reachability`.
   */
  private def updateReachability(subject: Subject, reachability: SBRReachabilityStatus): VersionedReachability =
    reachabilities
      .get(subject)
      .map(_.update(reachability))
      .getOrElse(VersionedReachability.init(reachability))

  /**
   * Update the contention of the observation of `subject` by `observer`
   * by the cluster node `node`.
   */
  def withContention(protester: UniqueAddress,
                     observer: Observer,
                     subject: Subject,
                     version: Version): SBReachabilityReporterState = {
    val contentions0  = contentions.getOrElse(subject, Map.empty)
    val oldContention = contentions0.getOrElse(observer, ContentionAggregator.empty)

    if (oldContention.version === version) {
      copy(
        reachabilities = indirectlyConnected(subject),
        contentions = contentions + (subject -> (contentions0 + (observer -> oldContention
          .disagree(protester))))
      )
    } else if (oldContention.version < version) {
      // First contention for this version.
      // Forget the old version and create a new one.
      copy(
        reachabilities = indirectlyConnected(subject),
        contentions = contentions + (subject -> (contentions0 + (observer -> ContentionAggregator(
          Set(protester),
          version
        ))))
      )
    } else {
      // Ignore the contention. It is for an older
      // detection of `subject` by `observer`.
      this
    }
  }

  def withContentions(
    newContentions: Map[Subject, Map[Observer, ContentionAggregator]]
  ): SBReachabilityReporterState = {
    val allContentions = contentions.toList ++ newContentions.toList // .toList to preserve duplicates

    val contentionsGroupedBySubject = allContentions.groupBy(_._1)

    val mergedContentions = contentionsGroupedBySubject.map {
      case (k, vs) => k -> vs.map(_._2).fold(Map.empty)(_ |+| _)
    }

    copy(contentions = mergedContentions)
  }

  /**
   * Remove the node.
   */
  def remove(node: UniqueAddress): SBReachabilityReporterState = {
    val updatedM = (contentions - node).mapValues { observers =>
      (observers - node).mapValues(_.agree(node))
    }

    val updatedReachabilities = updatedM.flatMap {
      case (subject, contentions) =>
        if (contentions.isEmpty) {
          // No contentions are left for the subject
          // as the disputed observer has been removed.
          // The subject becomes reachable.
          Some(subject -> updateReachability(subject, Reachable))
        } else if (contentions.nonEmpty && contentions.values.forall(_.disagreeing.isEmpty)) {
          // There are still disputed observers but everyone agrees with them.
          // The subject becomes unreachable.
          Some(subject -> updateReachability(subject, Unreachable))
        } else {
          None
        }
    }

    val pathToRemove = pathAtAddress(node.address, selfPath)

    copy(
      reachabilities = reachabilities ++ updatedReachabilities - node,
      // Subjects whose reachabilities have been updated are removed
      // as for all of them there is no disputed observer or all the
      // observers agree.
      contentions = updatedM -- updatedReachabilities.keySet,
      pendingContentionAcks = pendingContentionAcks - pathToRemove,
      pendingIntroductionAcks = pendingIntroductionAcks - pathToRemove
    )
  }

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

      copy(pendingContentionAcks = newPendingContentionAcks)
    }

  def expectIntroductionAck(ack: IntroductionAck): SBReachabilityReporterState =
    copy(pendingIntroductionAcks = pendingIntroductionAcks + (ack.from -> ack))

  def registerIntroductionAck(ack: IntroductionAck): SBReachabilityReporterState =
    copy(pendingIntroductionAcks = pendingIntroductionAcks - ack.from)

  /**
   * Set the subject as indirectly connected.
   */
  private def indirectlyConnected(subject: Subject): Map[UniqueAddress, VersionedReachability] = {
    val diff = reachabilities
      .get(subject)
      .map(_.update(IndirectlyConnected))
      .getOrElse(VersionedReachability.init(IndirectlyConnected))

    reachabilities + (subject -> diff)
  }
}

object SBReachabilityReporterState {
  type Observer = UniqueAddress
  type Subject  = UniqueAddress
  type Version  = Long

  def apply(selfPath: ActorPath): SBReachabilityReporterState =
    SBReachabilityReporterState(selfPath, Map.empty, Map.empty, Map.empty, Map.empty)

  /**
   * Represents the reachability of a node.
   */
  final case class VersionedReachability(reachability: SBRReachabilityStatus, retrieved: Boolean) {
    // lazy else the computation of the hashcode explodes
    lazy val tagAsRetrieved: VersionedReachability = copy(retrieved = true)

    def update(r: SBRReachabilityStatus): VersionedReachability =
      if (r != reachability) copy(reachability = r, retrieved = false) else this
  }

  object VersionedReachability {
    def init(r: SBRReachabilityStatus): VersionedReachability = VersionedReachability(r, retrieved = false)
  }

  /**
   * Represents a contention against a detection by a node. The `version`
   * needs to be strictly increasing for each `observer`, `subject` pair.
   */
  final case class ContentionAggregator(disagreeing: Set[UniqueAddress], version: Version) {
    def disagree(node: UniqueAddress): ContentionAggregator = copy(disagreeing = disagreeing + node)

    def agree(node: UniqueAddress): ContentionAggregator = copy(disagreeing = disagreeing - node)

    def merge(aggregator: ContentionAggregator): ContentionAggregator =
      if (version > aggregator.version) this
      else if (version < aggregator.version) aggregator
      else this.copy(disagreeing = disagreeing ++ aggregator.disagreeing)
  }

  object ContentionAggregator {
    val empty: ContentionAggregator = ContentionAggregator(Set.empty, 0)

    implicit val contentionAggregatorMonoid: Monoid[ContentionAggregator] = new Monoid[ContentionAggregator] {
      override val empty: ContentionAggregator                                                     = ContentionAggregator.empty
      override def combine(x: ContentionAggregator, y: ContentionAggregator): ContentionAggregator = x.merge(y)
    }
  }
}
