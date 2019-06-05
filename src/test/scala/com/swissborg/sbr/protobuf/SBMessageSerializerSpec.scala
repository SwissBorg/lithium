package com.swissborg.sbr.protobuf

import akka.actor.ActorSystem
import akka.serialization.SerializationExtension
import akka.testkit.TestKit
import com.swissborg.sbr.SBSpec
import com.swissborg.sbr.reachability.SBReachabilityReporter.{Contention, ContentionAck, IntroductionAck}

class SBMessageSerializerSpec extends TestKit(ActorSystem("test")) with SBSpec {
  private val contentionSerializer    = SerializationExtension(system).findSerializerFor(classOf[Contention])
  private val contentionAckSerializer = SerializationExtension(system).findSerializerFor(classOf[ContentionAck])
//  private val introductionSerializer    = SerializationExtension(system).findSerializerFor(classOf[Introduction])
  private val introductionAckSerializer = SerializationExtension(system).findSerializerFor(classOf[IntroductionAck])

  "SBMessageSerializer" must {
    "Contention round-trip" in {
      forAll { contention: Contention =>
        val bytes = contentionSerializer.toBinary(contention)
        contentionSerializer.fromBinary(bytes) shouldBe contention
      }
    }

    "ContentionAck round-trip" in {
      forAll { contentionAck: ContentionAck =>
        val bytes = contentionAckSerializer.toBinary(contentionAck)
        contentionAckSerializer.fromBinary(bytes) match {
          case ack: ContentionAck => ack should ===(contentionAck)
          case other              => fail(s"$other")
        }
      }
    }

    // TODO arbitrary is super duper slow
//    "Introduction round-trip" in {
//      forAll { introduction: Introduction =>
//        val bytes = introductionSerializer.toBinary(introduction)
//        introductionSerializer.fromBinary(bytes) shouldBe introduction
//      }
//    }

    "IntroductionAck round-trip" in {
      forAll { introductionAck: IntroductionAck =>
        val bytes = introductionAckSerializer.toBinary(introductionAck)
        introductionAckSerializer.fromBinary(bytes) shouldBe introductionAck
      }
    }
  }
}
