package com.aigreentick.services.storage.infrastructure.observability;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Minimal IPv4/IPv6 CIDR containment test, for the trusted-proxy allowlist.
 *
 * <p>Hand-written rather than pulled from a dependency: the whole behaviour is
 * thirty lines, and it sits on a security boundary where an opaque transitive
 * upgrade changing semantics would be worse than maintaining it.
 */
public final class CidrMatcher {

    private CidrMatcher() {
    }

    public static boolean matches(String cidr, String address) {
        if (cidr == null || address == null) {
            return false;
        }
        try {
            int slash = cidr.indexOf('/');
            if (slash < 0) {
                return cidr.equals(address);
            }
            byte[] network = InetAddress.getByName(cidr.substring(0, slash)).getAddress();
            byte[] candidate = InetAddress.getByName(address).getAddress();
            if (network.length != candidate.length) {
                return false;                       // mixed IPv4/IPv6, no match
            }
            int prefixBits = Integer.parseInt(cidr.substring(slash + 1));
            int fullBytes = prefixBits / 8;
            int remainderBits = prefixBits % 8;

            for (int i = 0; i < fullBytes; i++) {
                if (network[i] != candidate[i]) {
                    return false;
                }
            }
            if (remainderBits == 0) {
                return true;
            }
            int mask = (0xFF00 >> remainderBits) & 0xFF;
            return (network[fullBytes] & mask) == (candidate[fullBytes] & mask);

        } catch (UnknownHostException | NumberFormatException | IndexOutOfBoundsException e) {
            return false;
        }
    }
}
