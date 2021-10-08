package io.qalipsis.demo.services

import assertk.all
import assertk.assertThat
import assertk.assertions.any
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.hasSize
import assertk.assertions.prop
import assertk.assertions.startsWith
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.search.SearchHit
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.elasticsearch.ElasticsearchContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import kotlin.math.pow

/**
 * Elasticsearch service integration tests.
 *
 * This class provides integration tests for elasticsearch service.
 *
 * @author Alexander Sosnovsky
 */
@Testcontainers
@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class ElasticsearchServiceIntegrationTest : TestPropertyProvider {

    @Inject
    private lateinit var elasticsearchService: ElasticsearchService

    @Inject
    private lateinit var client: RestHighLevelClient

    override fun getProperties(): MutableMap<String, String> {
        return mutableMapOf(
            "micronaut.session.http.redis.enabled" to "false",
            "messaging.rabbitmq.enabled" to "false",
            "messaging.kafka.enabled" to "false",
            "elasticsearch.http-hosts" to "http://${es7.httpHostAddress}",
        )
    }

    @AfterEach
    fun tearDown() {
        // Removes all the created indices.
        client.indices().delete(DeleteIndexRequest("*"), RequestOptions.DEFAULT)
        client.indices().refresh(RefreshRequest("*"), RequestOptions.DEFAULT)
    }

    @Test
    fun `should save the records`() = runBlocking {
        // when
        val keyForSave1 = """this-is-a-key"""
        val dataForSave1 = """{"test":"data-1"}"""
        val dataForSave2 = """{"test":"data-2"}"""
        elasticsearchService.save(
            listOf(
                keyForSave1 to dataForSave1,
                null to dataForSave2
            )
        )

        // Forces the documents to be indexed and available for search.
        client.indices().refresh(RefreshRequest("*"), RequestOptions.DEFAULT)

        // then
        val documents = client.search(SearchRequest("*"), RequestOptions.DEFAULT)
        assertThat(documents.hits.hits.toList()).all {
            hasSize(2)
            any {
                it.prop(SearchHit::getSourceAsString).all {
                    startsWith("""{"test":"data-1","@savingTimestamp":""")
                    contains(""""@messageKey":"this-is-a-key"}""")
                }
            }
            any {
                it.prop(SearchHit::getSourceAsString).all {
                    startsWith("""{"test":"data-2","@savingTimestamp":""")
                    doesNotContain(""""@messageKey":""")
                }
            }
        }
    }

    companion object {

        @Container
        @JvmStatic
        private val es7 =
            ElasticsearchContainer(DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:7.9.2")).apply {
                withCreateContainerCmdModifier { cmd ->
                    cmd.hostConfig!!.withMemory(512 * 1024.0.pow(2).toLong()).withCpuCount(2)
                }
                withEnv("ES_JAVA_OPTS", "-Xms256m -Xmx256m")
            }
    }
}
