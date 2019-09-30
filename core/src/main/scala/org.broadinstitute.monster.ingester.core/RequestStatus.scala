package org.broadinstitute.monster.ingester.core

import java.time.OffsetDateTime

case class RequestStatus (submitted: OffsetDateTime, statusCounts: List[(Long, String)])
