package com.swissborg.sbr

import akka.actor.Address

final case class SBSeenChanged(convergence: Boolean, seenBy: Set[Address])
