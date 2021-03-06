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

package arcs.core.data

import arcs.core.crdt.CrdtEntity
import arcs.core.crdt.internal.VersionMap
import arcs.core.type.Type

data class Schema(
    val names: List<SchemaName>,
    val fields: SchemaFields,
    val description: SchemaDescription
) {
    val name: SchemaName?
        get() = names.firstOrNull()

    private val emptyRawEntity: RawEntity
        get() = RawEntity(
            singletonFields = fields.singletons,
            collectionFields = fields.collections
        )

    fun toLiteral(): Literal = Literal(names, fields, description)

    fun createCrdtEntityModel(): CrdtEntity = CrdtEntity(VersionMap(), emptyRawEntity)

    data class Literal(
        val names: List<SchemaName>,
        val fields: SchemaFields,
        val description: SchemaDescription
    ) : arcs.core.common.Literal {
        fun toJson(): String {
            // TODO: Actually use a json serializer when we're ready for it.
            return "{\"names\":[\"${names.joinToString { "\"$it\"" }}\"]}"
        }
    }

    companion object {
        fun fromLiteral(@Suppress("UNUSED_PARAMETER") literal: arcs.core.common.Literal): Schema {
            TODO("Implement me.")
        }
    }
}

/** Defines a [Type] that's capable of providing a schema for its entities. */
interface EntitySchemaProviderType : Type {
    /** [Schema] for the entity/entities managed by this [Type]. */
    val entitySchema: Schema?
}
