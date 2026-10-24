package org.ntust.app.tigerduck.shared.tls

import java.util.Base64

/**
 * The Ed25519 public keys a pin document may be signed with (TLS pins spec, A3): TigerSync's
 * current key and one standby key, by `key_id`, each the base64 (standard) of the raw 32-byte
 * key. A document signed by any other key is refused.
 *
 * TODO(tls-pins): placeholder. TigerSync's keys aren't published yet; fill in the current and
 * standby keys from the backend once they are. Until then the set is empty, so no document
 * verifies and the apps keep their built-in pins. Never put a test key here: tests pass their own
 * keys to [PinDocumentVerifier].
 */
object PinDocumentKeys {

    private val published: Map<String, String> = emptyMap()

    val keys: Map<String, ByteArray> = published.mapValues { (_, key) -> Base64.getDecoder().decode(key) }
}
