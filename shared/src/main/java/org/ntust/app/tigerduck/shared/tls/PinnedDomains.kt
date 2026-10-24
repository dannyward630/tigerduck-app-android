package org.ntust.app.tigerduck.shared.tls

/**
 * The domains network_security_config.xml pins, as its domain-configs that carry a pin-set name
 * them. The pins themselves stay in the XML, where the platform checks them; this list only
 * says which hosts a [PinnedTrustManager] may let a student continue to when their certificate
 * stops matching. NetworkSecurityConfigTest keeps it in step with the phone's and the watch's
 * XML.
 */
object PinnedDomains {

    data class Domain(val name: String, val includeSubdomains: Boolean)

    val all: List<Domain> = listOf(
        Domain("api.lib.ntust.edu.tw", includeSubdomains = true),
        Domain("ntust.edu.tw", includeSubdomains = true),
    )

    /**
     * The pinned domain [host] falls under, or null for a host no pin-set covers. As the
     * platform picks a host's domain-config, the most specific name wins: the library's own
     * pins, not the school's, for `api.lib.ntust.edu.tw`.
     */
    fun of(host: String, domains: List<Domain> = all): String? {
        val name = host.trimEnd('.').lowercase()
        return domains
            .filter { name == it.name || (it.includeSubdomains && name.endsWith(".${it.name}")) }
            .maxByOrNull { it.name.length }
            ?.name
    }
}
