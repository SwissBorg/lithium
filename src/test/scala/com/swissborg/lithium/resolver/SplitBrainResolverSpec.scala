package com.swissborg.lithium.resolver

import akka.actor.{ActorSystem, Props}
import akka.cluster.Cluster
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import cats.Id
import cats.effect.SyncIO
import com.swissborg.lithium.WorldView
import com.swissborg.lithium.strategy.Decision.DownIndirectlyConnected
import com.swissborg.lithium.strategy.{Decision, DownAll, IndirectlyConnected, KeepMajority, Strategy, Union}
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfterAll, Matchers}

import scala.concurrent.duration._

class SplitBrainResolverSpec
    extends TestKit(ActorSystem("lithium"))
    with ImplicitSender
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll {
  "SplitBrainResolver" must {
    "trigger the DownAll strategy" in {
      case object DowningAll
      case object ResolvingSplitBrain

      val probe = TestProbe()

      val splitBrainResolver = system.actorOf(
        Props(new SplitBrainResolver(new KeepMajority[SyncIO](KeepMajority.Config("")), 1.second, None, true) {
          override protected def downAll(worldView: WorldView): SyncIO[Unit] = SyncIO(probe.ref ! DowningAll)

          override protected def resolveSplitBrain(worldView: WorldView): SyncIO[Unit] =
            SyncIO(probe.ref ! ResolvingSplitBrain)
        })
      )

      splitBrainResolver ! SplitBrainResolver.DownAll(WorldView.init(Cluster(system).selfMember))

      probe.expectMsgPF() {
        case m => m
      } shouldBe DowningAll
    }

    "trigger the configured strategy" in {
      case object DowningAll
      case object ResolvingSplitBrain

      val probe = TestProbe()

      val splitBrainResolver = system.actorOf(
        Props(new SplitBrainResolver(new KeepMajority[SyncIO](KeepMajority.Config("")), 1.second, None, true) {
          override protected def downAll(worldView: WorldView): SyncIO[Unit] = SyncIO(probe.ref ! DowningAll)

          override protected def resolveSplitBrain(worldView: WorldView): SyncIO[Unit] =
            SyncIO(probe.ref ! ResolvingSplitBrain)
        })
      )

      splitBrainResolver ! SplitBrainResolver.ResolveSplitBrain(WorldView.init(Cluster(system).selfMember))

      probe.expectMsgPF() {
        case m => m
      } shouldBe ResolvingSplitBrain
    }

    "get the decision from the configured strategy + IndirectlyConnected" in {
      final case class RunningDecision(decision: Decision)

      val probe = TestProbe()

      val strategy = new KeepMajority[SyncIO](KeepMajority.Config(""))
      val strategyWithIndirectlyConnected: Union[SyncIO, Strategy, IndirectlyConnected] =
        new Union(strategy, new IndirectlyConnected)

      val splitBrainResolver = system.actorOf(
        Props(new SplitBrainResolver(strategy, 1.second, None, true) {
          override protected def runDecision(decision: Decision): SyncIO[Unit] =
            SyncIO(probe.ref ! RunningDecision(decision))
        })
      )

      val worldView = WorldView.init(Cluster(system).selfMember)

      splitBrainResolver ! SplitBrainResolver.ResolveSplitBrain(worldView)

      probe.expectMsg(RunningDecision(strategyWithIndirectlyConnected.takeDecision(worldView).unsafeRunSync()))
    }

    "get the decision from the DownAll strategy" in {
      final case class RunningDecision(decision: Decision)

      val probe = TestProbe()

      val splitBrainResolver = system.actorOf(
        Props(new SplitBrainResolver(new KeepMajority[SyncIO](KeepMajority.Config("")), 1.second, None, true) {
          override protected def runDecision(decision: Decision): SyncIO[Unit] =
            SyncIO(probe.ref ! RunningDecision(decision))
        })
      )

      val worldView = WorldView.init(Cluster(system).selfMember)

      splitBrainResolver ! SplitBrainResolver.DownAll(worldView)

      probe.expectMsg(RunningDecision(new DownAll[Id].takeDecision(worldView)))
    }

  }
}
