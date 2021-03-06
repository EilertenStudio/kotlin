fun <T> eval(fn: () -> T) = fn()

class IntentionsBundle {
    companion object {
        fun message(key: String): String {
            return key + BUNDLE
        }

        fun message2(key: String): String {
            return eval { key + BUNDLE }
        }

        private const val BUNDLE = "K"
    }
}


fun box(): String {
    if (IntentionsBundle.message("O") != "OK") return "fail 1: ${IntentionsBundle.message("O")}"

    return IntentionsBundle.message2("O")
}