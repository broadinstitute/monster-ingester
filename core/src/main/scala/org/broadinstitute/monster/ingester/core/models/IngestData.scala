package org.broadinstitute.monster.ingester.core.models

/**
 * Class that represents the data body associated with ingest requests to the Ingester.
 *
 * @param tables list of JobData to capture key information for ingest requests.
 */
case class IngestData(tables: List[JobData])
