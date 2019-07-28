package akka.cluster.swissborg

import akka.actor.{Actor, Props}
import akka.cluster.ClusterEvent.ReachabilityChanged
import com.swissborg.lithium.converter._

/**
  * Publishes a [[LithiumReachabilityChanged]] to the event stream when receiving
  * a [[ReachabilityChanged]] event.
  */
class LithiumReachabilityPublisher extends Actor {

  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  override def receive: Receive = {
    case ReachabilityChanged(r) =>
      context.system.eventStream.publish(LithiumReachabilityChanged(LithiumReachability.fromReachability(r)))
  }

  override def preStart(): Unit =
    discard(context.system.eventStream.subscribe(self, classOf[ReachabilityChanged]))

  override def postStop(): Unit = context.system.eventStream.unsubscribe(self)
}

object LithiumReachabilityPublisher {
  val props: Props = Props(new LithiumReachabilityPublisher)
}
