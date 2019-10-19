package akka.cluster.swissborg

import akka.actor.ActorSystem
import akka.cluster.ClusterEvent.{ReachabilityChanged, SeenChanged}
import akka.cluster.Reachability
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.swissborg.lithium.internals.{LithiumReachabilityChanged, LithiumSeenChanged}
import org.scalatest.{BeforeAndAfterAll, Matchers}
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration._

import scala.collection.immutable.IndexedSeq

class ClusterInternalsPublisherSpec
    extends TestKit(ActorSystem("lithium"))
    with ImplicitSender
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll {
  override def afterAll(): Unit = TestKit.shutdownActorSystem(system)

  "ClusterInternalsPublisher" must {
    "convert and publish ReachabilityChanged events" in {
      system.actorOf(ClusterInternalsPublisher.props)

      val probe = TestProbe()

      system.eventStream.subscribe(probe.ref, classOf[LithiumReachabilityChanged])
      system.eventStream.publish(ReachabilityChanged(Reachability(IndexedSeq.empty[Reachability.Record], Map.empty)))

      probe.expectMsgType[LithiumReachabilityChanged]
    }

    "convert and publish SeenChanged events" in {
      system.actorOf(ClusterInternalsPublisher.props)

      val probe = TestProbe()

      system.eventStream.subscribe(probe.ref, classOf[LithiumSeenChanged])
      system.eventStream.publish(SeenChanged(false, Set.empty))

      probe.expectMsg(LithiumSeenChanged(false, Set.empty))
    }
  }
}
