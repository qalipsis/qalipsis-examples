package io.qalipsis.demo

import io.micronaut.runtime.Micronaut.*

fun main(args: Array<String>) {
	build()
	    .args(*args)
		.packages("io.qalipsis")
		.start()
}

