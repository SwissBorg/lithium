package com.swissborg.sbr.protobuf

import com.swissborg.sbr.SBSpec
import com.swissborg.sbr.failuredetector.SBFailureDetector.{Contention, ContentionAck}
import com.swissborg.sbr.implicits._

class SBMessageSerializerSpec extends SBSpec {
  private val serializer = new SBMessageSerializer

  "SBMessageSerializer" must {
    "Contention round-trip" in {
      forAll { contention: Contention =>
        val bytes = serializer.toBinary(contention)
        serializer.fromBinary(bytes, serializer.manifest(contention)) shouldBe contention
      }
    }

    "ContentionAck round-trip" in {
      forAll { contentionAck: ContentionAck =>
        val bytes = serializer.toBinary(contentionAck)
        serializer.fromBinary(bytes, serializer.manifest(contentionAck)) match {
          case ack: ContentionAck => ack should ===(contentionAck)
          case other              => fail(s"$other")
        }
      }
    }
  }
}
