package com.swissborg.sbr

class HealthyWorldViewSpec extends SBSpec {
  "HealthyWorldView" must {
    "not have unreachable nodes" in {
      forAll { worldView: HealthyWorldView =>
        worldView.unreachableNodes shouldBe empty
      }
    }
  }
}
