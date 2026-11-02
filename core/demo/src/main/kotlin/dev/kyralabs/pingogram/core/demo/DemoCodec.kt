package dev.kyralabs.pingogram.core.demo

import org.drinkless.tdlib.TdApi
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.lang.reflect.Field
import java.lang.reflect.Modifier

/**
 * Writes TDLib's objects to bytes and back, so the demo's journal can keep the functions the user's actions sent
 * (`docs/adr/0034-demo-mode-for-store-review.md`): every public field of a `TdApi` class, by name, read and written
 * by reflection. TDLib's own keep rules (`-keep class org.drinkless.tdlib.** { *; }`) keep them through R8.
 *
 * The format is the demo's own, tied to the pinned TDLib: a TDLib bump that renames a field makes the lines it wrote
 * unreadable, and the journal leaves them out, as a line cut short by a crash.
 */
internal object DemoCodec {
    fun encode(value: TdApi.Object): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { write(it, value) }
        return bytes.toByteArray()
    }

    fun <T : TdApi.Object> decode(bytes: ByteArray): T {
        val value = DataInputStream(ByteArrayInputStream(bytes)).use(::read)
        // The bytes were written by encode() for a T.
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    /** Calls [visit] for every TDLib object inside [value], [value] included, depth first. */
    fun walk(
        value: TdApi.Object,
        visit: (TdApi.Object) -> Unit,
    ) {
        visit(value)
        fieldsOf(value.javaClass).forEach { field -> walkValue(field.get(value), visit) }
    }

    private fun walkValue(
        value: Any?,
        visit: (TdApi.Object) -> Unit,
    ) {
        when (value) {
            is TdApi.Object -> walk(value, visit)
            is Array<*> -> value.forEach { walkValue(it, visit) }
            else -> Unit
        }
    }

    // One branch per kind of value a TDLib field holds.
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun write(
        out: DataOutputStream,
        value: Any?,
    ) {
        when (value) {
            null -> {
                out.writeByte(NULL)
            }

            is Int -> {
                out.writeByte(INT)
                out.writeInt(value)
            }

            is Long -> {
                out.writeByte(LONG)
                out.writeLong(value)
            }

            is Double -> {
                out.writeByte(DOUBLE)
                out.writeDouble(value)
            }

            is Boolean -> {
                out.writeByte(BOOLEAN)
                out.writeBoolean(value)
            }

            is String -> {
                out.writeByte(STRING)
                writeString(out, value)
            }

            is ByteArray -> {
                out.writeByte(BYTES)
                out.writeInt(value.size)
                out.write(value)
            }

            is IntArray -> {
                out.writeByte(INTS)
                out.writeInt(value.size)
                value.forEach(out::writeInt)
            }

            is LongArray -> {
                out.writeByte(LONGS)
                out.writeInt(value.size)
                value.forEach(out::writeLong)
            }

            is Array<*> -> {
                out.writeByte(ARRAY)
                writeString(out, value.javaClass.componentType!!.name)
                out.writeInt(value.size)
                value.forEach { write(out, it) }
            }

            is TdApi.Object -> {
                out.writeByte(OBJECT)
                writeString(out, value.javaClass.name)
                val fields = fieldsOf(value.javaClass)
                out.writeInt(fields.size)
                fields.forEach { field ->
                    writeString(out, field.name)
                    write(out, field.get(value))
                }
            }

            else -> {
                error("Not a TDLib value: ${value.javaClass.name}")
            }
        }
    }

    // One branch per kind of value, the counterpart of write().
    @Suppress("CyclomaticComplexMethod")
    private fun read(input: DataInputStream): Any? =
        when (val tag = input.readByte().toInt()) {
            NULL -> {
                null
            }

            INT -> {
                input.readInt()
            }

            LONG -> {
                input.readLong()
            }

            DOUBLE -> {
                input.readDouble()
            }

            BOOLEAN -> {
                input.readBoolean()
            }

            STRING -> {
                readString(input)
            }

            BYTES -> {
                ByteArray(input.readInt()).also(input::readFully)
            }

            INTS -> {
                IntArray(input.readInt()) { input.readInt() }
            }

            LONGS -> {
                LongArray(input.readInt()) { input.readLong() }
            }

            ARRAY -> {
                val component = classOf(readString(input))
                val size = input.readInt()
                val array =
                    java.lang.reflect.Array
                        .newInstance(component, size)
                repeat(size) {
                    java.lang.reflect.Array
                        .set(array, it, read(input))
                }
                array
            }

            OBJECT -> {
                val type = classOf(readString(input))
                val value = type.getDeclaredConstructor().newInstance()
                val fields = fieldsOf(type).associateBy { it.name }
                repeat(input.readInt()) {
                    val field = fields[readString(input)] ?: error("Unknown field of ${type.name}")
                    field.set(value, read(input))
                }
                value
            }

            else -> {
                error("Unknown tag $tag")
            }
        }

    private fun classOf(name: String): Class<*> =
        when (name) {
            "java.lang.String" -> String::class.java
            "[B" -> ByteArray::class.java
            "[I" -> IntArray::class.java
            "[J" -> LongArray::class.java
            else -> Class.forName(name, false, TdApi::class.java.classLoader)
        }

    private fun fieldsOf(type: Class<*>): List<Field> =
        fieldCache.getOrPut(type) {
            type.fields.filter { !Modifier.isStatic(it.modifiers) }.sortedBy { it.name }
        }

    private val fieldCache = java.util.concurrent.ConcurrentHashMap<Class<*>, List<Field>>()

    private fun writeString(
        out: DataOutputStream,
        value: String,
    ) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        out.writeInt(bytes.size)
        out.write(bytes)
    }

    private fun readString(input: DataInputStream): String =
        String(ByteArray(input.readInt()).also(input::readFully), Charsets.UTF_8)

    private const val NULL = 0
    private const val INT = 1
    private const val LONG = 2
    private const val DOUBLE = 3
    private const val BOOLEAN = 4
    private const val STRING = 5
    private const val BYTES = 6
    private const val INTS = 7
    private const val LONGS = 8
    private const val ARRAY = 9
    private const val OBJECT = 10
}
