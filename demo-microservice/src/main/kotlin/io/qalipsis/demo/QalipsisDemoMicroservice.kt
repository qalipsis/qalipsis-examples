package io.qalipsis.demo

import io.micronaut.runtime.Micronaut.build

/**
 * @author Alexander Sosnovsky
 */
fun main(args: Array<String>) {
    build()
        .args(*args)
        .packages("io.qalipsis.demo")
        .start()
}

