package akka.cluster.swissborg

import akka.actor.{Actor, Props}
import akka.cluster.ClusterEvent.{ReachabilityChanged, SeenChanged}
import com.swissborg.lithium.internals._

/**
  * Actor making the [[SeenChanged]] and [[ReachabilityChanged]] event streams
  * accessible from outside the `akka.cluster` namespace.
  */
class ClusterInternalsPublisher extends Actor {

  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  override def receive: Receive = {
    case ReachabilityChanged(r) =>
      context.system.eventStream.publish(LithiumReachabilityChanged(LithiumReachability.fromReachability(r)))

    case SeenChanged(convergence, seenBy) =>
      context.system.eventStream.publish(LithiumSeenChanged(convergence, seenBy))
  }

  override def preStart(): Unit = {
    discard(context.system.eventStream.subscribe(self, classOf[ReachabilityChanged]))
    discard(context.system.eventStream.subscribe(self, classOf[SeenChanged]))
  }

  override def postStop(): Unit = context.system.eventStream.unsubscribe(self)
}

object ClusterInternalsPublisher {
  val props: Props = Props(new ClusterInternalsPublisher)
}
