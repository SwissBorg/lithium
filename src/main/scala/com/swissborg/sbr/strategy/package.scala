package com.swissborg.sbr

import eu.timepit.refined.W
import eu.timepit.refined.string.MatchesRegex

package object strategy {
  type SBAddress = MatchesRegex[
    W.`"([0-9A-Za-z]+.)*[0-9A-Za-z]+://[0-9A-Za-z]+@([0-9A-Za-z]+.)*[0-9A-Za-z]+:[0-9]+"`.T
  ]
}
