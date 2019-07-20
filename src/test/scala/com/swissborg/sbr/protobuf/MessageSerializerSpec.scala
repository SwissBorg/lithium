package com.swissborg.sbr
package protobuf

import akka.actor.ActorSystem
import akka.serialization.SerializationExtension
import akka.testkit.TestKit
import com.swissborg.sbr.reachability.ReachabilityReporter.{
  SuspiciousDetection,
  SuspiciousDetectionAck
}

class MessageSerializerSpec extends TestKit(ActorSystem("test")) with SBSpec {
  private val suspiciousDetectionSerializer =
    SerializationExtension(system).findSerializerFor(classOf[SuspiciousDetection])
  private val suspiciousDetectionAckSerializer =
    SerializationExtension(system).findSerializerFor(classOf[SuspiciousDetectionAck])

  "SBMessageSerializer" must {
    "SuspiciousDetection round-trip" in {
      forAll { suspiciousDetection: SuspiciousDetection =>
        val bytes = suspiciousDetectionSerializer.toBinary(suspiciousDetection)
        suspiciousDetectionSerializer.fromBinary(bytes) shouldBe suspiciousDetection
      }
    }

    "SuspiciousDetectionAck round-trip" in {
      forAll { suspiciousDetectionAck: SuspiciousDetectionAck =>
        val bytes = suspiciousDetectionAckSerializer.toBinary(suspiciousDetectionAck)
        suspiciousDetectionAckSerializer.fromBinary(bytes) match {
          case ack: SuspiciousDetectionAck => ack should ===(suspiciousDetectionAck)
          case other                       => fail(s"$other")
        }
      }
    }
  }
}
