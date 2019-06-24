package akka.cluster.swissborg

import akka.actor.{Actor, Props}
import akka.cluster.ClusterEvent.ReachabilityChanged
import com.swissborg.sbr.converter._

/**
  * Converts [[ReachabilityChanged]] to [[SBReachabilityChanged]] events
  * and publishes them to the event stream.
  */
class ConverterActor extends Actor {
  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  override def receive: Receive = {
    case ReachabilityChanged(r) =>
      context.system.eventStream.publish(SBReachabilityChanged(SBReachability(r)))
  }

  override def preStart(): Unit =
    discard(context.system.eventStream.subscribe(self, classOf[ReachabilityChanged]))

  override def postStop(): Unit = context.system.eventStream.unsubscribe(self)
}

object ConverterActor {
  val props: Props = Props(new ConverterActor)
}
