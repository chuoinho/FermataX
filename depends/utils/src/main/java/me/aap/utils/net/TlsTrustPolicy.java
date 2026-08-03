package me.aap.utils.net;

/** TLS certificate-validation policy attached explicitly to each outbound connection. */
public enum TlsTrustPolicy {
	STRICT,
	TRUST_ALL_USER_SOURCE
}
