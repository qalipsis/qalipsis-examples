package io.qalipsis.example.opencellid

import io.qalipsis.api.annotations.Property
import io.qalipsis.api.annotations.Scenario
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.rampup.regular
import io.qalipsis.api.scenario.scenario
import io.qalipsis.api.steps.collect
import io.qalipsis.api.steps.logErrors
import io.qalipsis.api.steps.map
import io.qalipsis.api.steps.onEach
import io.qalipsis.plugins.elasticsearch.Document
import io.qalipsis.plugins.elasticsearch.elasticsearch
import io.qalipsis.plugins.elasticsearch.save.save
import io.qalipsis.plugins.jackson.csv.csvToMap
import io.qalipsis.plugins.jackson.jackson
import org.apache.http.HttpHost
import org.elasticsearch.client.RestClient
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

class ImportOpenCellId {

    val totalSaving = AtomicInteger()

    val totalSaved = AtomicInteger()

    @Scenario
    fun myScenario(
        @Property("source") sourceFile: String,
        @Property("target.urls") elasticsearchUrls: String,
        @Property("target.index", orElse = "masterloader_production_geolocating-cell-tower") elasticsearchIndex: String,
        @Property("target.type", orElse = "celltower") elasticsearchType: String,
    ) {
        scenario("import-open-cell-id") {
            minionsCount = 1
            rampUp {
                this.regular(1000L, minionsCount)
            }
        }
            .start()
            .jackson().csvToMap {
                file(sourceFile)
                header {
                    withHeader()
                    columnSeparator(',')
                    column("radio")
                    column("mcc")
                    column("net")
                    column("area")
                    column("cell")
                    column("unit")
                    column("lon").double()
                    column("lat").double()
                    column("range").double()
                    column("samples")
                    column("changeable")
                    column("created")
                    column("updated")
                    column("averageSignal")
                }
                sequential()
                iterate(Long.MAX_VALUE)
                name = "read-csv"
            }
            .map {
                val values = it.value
                val id = "${values["mcc"]}-${values["net"]}-${values["area"]}-${values["cell"]}"
                id to """{"id":"$id","radio":"${values["radio"]}","range":${values["range"]},"location":{"lon":${values["lon"]},"lat":${values["lat"]}}}""".trimIndent()
            }
            .collect(Duration.ofSeconds(60), 30_000)
            .onEach {
                logger.info { "Total saving: ${totalSaving.addAndGet(it.size)}, saved: ${totalSaved.get()}" }
            }
            .elasticsearch()
            .save {
                client {
                    val hosts = elasticsearchUrls.split(",").map { HttpHost.create(it.trim()) }
                    RestClient.builder(*hosts.toTypedArray()).setCompressionEnabled(true).build()
                }
                documents { _, input ->
                    input.map { (id, document) ->
                        Document(elasticsearchIndex, elasticsearchType, id, document)
                    }
                }
                name = "save-into-es"
            }
            .onEach {
                logger.info { "Total saving: ${totalSaving.get()}, saved: ${totalSaved.addAndGet(it.input.size)}" }
            }
            .logErrors(logger)
    }

    companion object {

        @JvmStatic
        private val logger = logger()
    }
}
