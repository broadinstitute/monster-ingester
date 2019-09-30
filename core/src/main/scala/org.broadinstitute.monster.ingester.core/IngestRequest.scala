package org.broadinstitute.monster.ingester.core

// this should represent the ingest request made to the monster ingester service
case class IngestRequest (tables: List[JobInfo])
