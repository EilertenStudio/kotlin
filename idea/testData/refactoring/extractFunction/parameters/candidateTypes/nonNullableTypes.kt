// PARAM_TYPES: kotlin.String, Comparable<String>, CharSequence, kotlin.Any
// PARAM_TYPES: X<kotlin.Any>
class X<T> {
    fun add(t: T) {

    }
}

// SIBLING:
fun foo(s: String?, x: X<Any>) {
    when {
        s != null -> <selection>x.add(s)</selection>
    }
}