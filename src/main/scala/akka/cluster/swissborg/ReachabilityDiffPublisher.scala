package akka.cluster.swissborg

import akka.actor.{Actor, Props}
import akka.cluster.ClusterEvent.ReachabilityChanged
import akka.cluster.Reachability
import com.swissborg.sbr.converter._

/**
  * Publishes a [[ReachabilityDiff]] to the event stream when based
  * on the previous and latest [[ReachabilityChanged]].
  */
@SuppressWarnings(Array("org.wartremover.warts.Var"))
class ReachabilityDiffPublisher extends Actor {
  var previousReachability: Option[Reachability] = None

  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  override def receive: Receive = {
    case ReachabilityChanged(r) =>
      context.system.eventStream
        .publish(ReachabilityDiffChanged(ReachabilityDiff(previousReachability, r)))

      previousReachability = Some(r)
  }

  override def preStart(): Unit =
    discard(context.system.eventStream.subscribe(self, classOf[ReachabilityChanged]))

  override def postStop(): Unit = context.system.eventStream.unsubscribe(self)
}

object ReachabilityDiffPublisher {
  val props: Props = Props(new ReachabilityDiffPublisher)
}
