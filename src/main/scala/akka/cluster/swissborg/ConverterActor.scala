package akka.cluster.swissborg

import akka.actor.{Actor, Props}
import akka.cluster.ClusterEvent.{ReachabilityChanged, SeenChanged}
import com.swissborg.sbr.{SBReachabilityChanged, SBSeenChanged}

/**
 * Converts and publishes to the event-stream [[ReachabilityChanged]] and [[SeenChanged]]
 * into events that are visible outside Akka's namespace.
 */
class ConverterActor extends Actor {
  override def receive: Receive = {
    case ReachabilityChanged(r)           => context.system.eventStream.publish(SBReachabilityChanged(SBReachability(r)))
    case SeenChanged(convergence, seenBy) => context.system.eventStream.publish(SBSeenChanged(convergence, seenBy))
  }

  override def preStart(): Unit = {
    context.system.eventStream.subscribe(self, classOf[ReachabilityChanged])
    context.system.eventStream.subscribe(self, classOf[SeenChanged])
  }

  override def postStop(): Unit = context.system.eventStream.unsubscribe(self)
}

object ConverterActor {
  val props: Props = Props(new ConverterActor)
}