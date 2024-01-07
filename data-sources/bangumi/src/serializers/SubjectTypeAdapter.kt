package me.him188.ani.datasources.bangumi.serializers

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import org.openapitools.client.models.SubjectType
import java.lang.reflect.Type


// Should be added to org.openapitools.client.infrastructure.Serializer.getMoshiBuilder
class SubjectTypeAdapter : JsonAdapter.Factory {
    override fun create(type: Type, annotations: MutableSet<out Annotation>, moshi: Moshi): JsonAdapter<*>? {
        if (type != SubjectType::class.java) return null
        return object : JsonAdapter<SubjectType>() {
            override fun fromJson(reader: com.squareup.moshi.JsonReader): SubjectType? {
                val value = reader.nextString()
                return SubjectType.decode(value)
            }

            override fun toJson(writer: com.squareup.moshi.JsonWriter, value: SubjectType?) {
                writer.value(SubjectType.encode(value))
            }
        }
    }
}
