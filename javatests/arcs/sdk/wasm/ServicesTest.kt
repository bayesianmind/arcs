/*
 * Copyright 2019 Google LLC.
 *
 * This code may only be used under the BSD style license found at
 * http://polymer.github.io/LICENSE.txt
 *
 * Code distributed by Google as part of this project is also subject to an additional IP rights
 * grant found at
 * http://polymer.github.io/PATENTS.txt
 */

package arcs.sdk.wasm

import arcs.sdk.Collection
import arcs.sdk.Particle

class ServicesTest : Particle() {
    private val output = Collection(this, "output") { ServicesTest_Output() }

    override fun init() {
        val url: String = resolveUrl("\$resolve-me")
        output.store(ServicesTest_Output("resolveUrl", tag = "", payload = url))

        serviceRequest("random.next", mapOf(), "first")
        serviceRequest("random.next", mapOf(), "second")
        serviceRequest("clock.now", mapOf("timeUnit" to "DAYS"))
    }

    override fun serviceResponse(call: String, response: Map<String, String>, tag: String) {
        val builder = StringBuilder()
        response.entries
            .map { entry -> "${entry.key}:${entry.value};" }
            .forEach { str -> builder.append(str) }
        val payload = builder.toString()

        output.store(ServicesTest_Output(call, tag, payload))
    }
}
