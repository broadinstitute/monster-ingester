package org.broadinstitute.monster.ingester.core.models

import java.time.OffsetDateTime

case class RequestSummary(submitted: OffsetDateTime, statusCounts: List[(Long, String)])
