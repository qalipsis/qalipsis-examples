package io.qalipsis.demo.services

import io.qalipsis.api.logging.LoggerHelper.logger
import jakarta.inject.Singleton
import org.elasticsearch.action.bulk.BulkRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.common.xcontent.XContentType

/**
 * Elasticsearch service.
 *
 * This class provides methods to work with elasticsearch.
 *
 * @author Alexander Sosnovsky
 */
@Singleton
class ElasticsearchService(private val client: RestHighLevelClient) {

    /**
     * Saves the records in elasticsearch.
     *
     * @param records records to save as pairs: the first member is the ID of the message, the second is the source
     */
    fun save(records: List<Pair<String?, String>>) {
        if (records.isNotEmpty()) {
            log.debug { "Saving the records to Elasticsearch: '$records'" }
            val request = BulkRequest(index)
            records.chunked(1000).forEach { chunk ->
                chunk.forEach { (key, value) ->
                    val actualKey = key?.takeIf(String::isNotBlank)
                    val clearedValue = value.trim().trimEnd('}').toByteArray()
                    val timestamp = ""","@savingTimestamp":${System.currentTimeMillis()}""".toByteArray()
                    val valueSuffix = if (actualKey != null) {
                        ""","@messageKey":"$actualKey"}"""
                    } else {
                        "}"
                    }.toByteArray()
                    request.add(IndexRequest().source(clearedValue + timestamp + valueSuffix, XContentType.JSON))
                }
                client.bulk(request, RequestOptions.DEFAULT)
            }
        }
    }

    private companion object {

        @JvmStatic
        val log = logger()

        const val index = "http-requests"
    }
}
