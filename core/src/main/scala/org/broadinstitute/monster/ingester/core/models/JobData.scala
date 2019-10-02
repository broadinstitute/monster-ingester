package org.broadinstitute.monster.ingester.core.models

/**
 * Class that represents each job request within an ingest request body made to the Ingester.
 *
 * @param path the path to the files to ingest (file or "directory" pattern).
 * @param tableName the name of the table in the Repo to ingest into.
 */
case class JobData(path: String, tableName: String)
