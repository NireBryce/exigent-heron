package net.breadthcharge.exigentheron.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * AGENTS.md §4.5's required realistic strings (bank OTP, delivery code,
 * a year, a price), plus the floor, the visibility gate, and the
 * downgrade-mechanics tests this class's own doc comment calls out.
 */
class SecretDetectorTest {

    private fun payload(body: String?, title: String? = "Alerts", visibility: Int = 1) =
        NotificationPayload(
            key = "k",
            packageName = "com.example.app",
            postTime = 0,
            title = title,
            body = body,
            isGroupSummary = false,
            isOngoing = false,
            visibility = visibility,
            contentHash = "irrelevant",
        )

    @Test
    fun `bank OTP is downgraded to a generic announcement, not spoken`() {
        val detector = SecretDetector()
        val p = payload(body = "Your verification code is 483921. Do not share it.")

        val result = detector.scan(Decision.Speak(p.body!!), p)

        assertThat(result).isInstanceOf(Decision.AnnounceOnly::class.java)
        result as Decision.AnnounceOnly
        assertThat(result.text).doesNotContain("483921")
    }

    @Test
    fun `delivery code is downgraded`() {
        val detector = SecretDetector()
        val p = payload(body = "Your package delivery code is 7734. Show this to the courier.")

        val result = detector.scan(Decision.Speak(p.body!!), p)

        assertThat(result).isInstanceOf(Decision.AnnounceOnly::class.java)
        result as Decision.AnnounceOnly
        assertThat(result.text).doesNotContain("7734")
    }

    @Test
    fun `a normal message containing a year is left alone`() {
        val detector = SecretDetector()
        val p = payload(body = "See you in 2026 for the reunion!")
        val decision = Decision.Speak(p.body!!)

        assertThat(detector.scan(decision, p)).isEqualTo(decision)
    }

    @Test
    fun `a normal message containing a price is left alone`() {
        val detector = SecretDetector()
        // Digit runs here are 2 chars each — below the \d{4,8} floor of
        // the OTP shape regex, same as a real price would be.
        val p = payload(body = "Your order total is \$49.99, thanks for shopping.")
        val decision = Decision.Speak(p.body!!)

        assertThat(detector.scan(decision, p)).isEqualTo(decision)
    }

    @Test
    fun `hardcoded floor suppresses a bare 6-digit body even without a keyword`() {
        val detector = SecretDetector()
        val p = payload(body = "482913")

        val result = detector.scan(Decision.Speak(p.body!!), p)

        assertThat(result).isEqualTo(Decision.Suppress(reason = "bare 6-digit body"))
    }

    @Test
    fun `floor does not apply to a 6-digit body embedded in a longer sentence`() {
        // The floor is specifically "the entire message body" per
        // AGENTS.md §4.5 — a 6-digit run inside a longer sentence with
        // no keyword nearby is judged by the proximity check instead,
        // not the unconditional floor.
        val detector = SecretDetector()
        val p = payload(body = "Meeting moved to room 482913 next week.")
        val decision = Decision.Speak(p.body!!)

        assertThat(detector.scan(decision, p)).isEqualTo(decision)
    }

    @Test
    fun `visibility-private downgrades regardless of content`() {
        val detector = SecretDetector()
        val p = payload(body = "Nothing suspicious here at all.", visibility = 0 /* VISIBILITY_PRIVATE */)

        val result = detector.scan(Decision.Speak(p.body!!), p)

        assertThat(result).isInstanceOf(Decision.AnnounceOnly::class.java)
    }

    @Test
    fun `visibility-secret downgrades regardless of content`() {
        val detector = SecretDetector()
        val p = payload(body = "Nothing suspicious here at all.", visibility = -1 /* VISIBILITY_SECRET */)

        val result = detector.scan(Decision.Speak(p.body!!), p)

        assertThat(result).isInstanceOf(Decision.AnnounceOnly::class.java)
    }

    @Test
    fun `never upgrades an existing Suppress`() {
        val detector = SecretDetector()
        val p = payload(body = "Totally ordinary text.")
        val decision = Decision.Suppress(reason = "some earlier rule")

        assertThat(detector.scan(decision, p)).isSameInstanceAs(decision)
    }

    @Test
    fun `an announce-only decision that still embeds the flagged body is suppressed, not left as-is`() {
        // Guards the specific failure mode this class's doc comment
        // calls out: a user Rule.template of "{body}" would otherwise
        // let an OTP straight through disguised as an "announcement".
        val detector = SecretDetector()
        val p = payload(body = "Your verification code is 483921.")
        val decision = Decision.AnnounceOnly(text = p.body!!) // template embedded {body} verbatim

        val result = detector.scan(decision, p)

        assertThat(result).isInstanceOf(Decision.Suppress::class.java)
    }

    @Test
    fun `an already-generic announce-only decision is left alone on OTP-shaped content`() {
        val detector = SecretDetector()
        val p = payload(body = "Your verification code is 483921.")
        val decision = Decision.AnnounceOnly(text = "New notification from Alerts")

        assertThat(detector.scan(decision, p)).isEqualTo(decision)
    }

    @Test
    fun `keyword matching is word-bounded, not substring`() {
        // "shopping" contains "pin" as a raw substring; "encoded"
        // contains "code". Neither should count as the keyword.
        val detector = SecretDetector()
        val p = payload(body = "Order #482913 confirmed, thanks for shopping and see the encoded receipt.")
        val decision = Decision.Speak(p.body!!)

        assertThat(detector.scan(decision, p)).isEqualTo(decision)
    }

    @Test
    fun `custom keyword list is honored`() {
        val detector = SecretDetector(keywords = listOf("magic word"))
        val p = payload(body = "The magic word is 123456, don't tell anyone.")

        val result = detector.scan(Decision.Speak(p.body!!), p)

        assertThat(result).isInstanceOf(Decision.AnnounceOnly::class.java)
    }
}
