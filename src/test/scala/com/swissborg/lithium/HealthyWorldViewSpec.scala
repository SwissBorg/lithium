package com.swissborg.lithium

class HealthyWorldViewSpec extends LithiumSpec {
  "HealthyWorldView" must {
    "not have unreachable nodes" in {
      forAll { worldView: HealthyWorldView =>
        worldView.unreachableNodes shouldBe empty
      }
    }
  }
}
