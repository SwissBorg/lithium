package com.swissborg.sbr.instances

import shapeless.tag.@@

object OrderingInstances extends OrderingInstances

trait OrderingInstances {
  implicit def taggedOrdering[A: Ordering, B]: Ordering[A @@ B] = Ordering[A].on(identity)
}
