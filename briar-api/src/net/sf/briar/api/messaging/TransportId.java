package net.sf.briar.api.messaging;

import java.util.Arrays;

/**
 * Type-safe wrapper for a byte array that uniquely identifies a transport
 * plugin.
 */
public class TransportId extends UniqueId {

	public TransportId(byte[] id) {
		super(id);
	}

	@Override
	public boolean equals(Object o) {
		if(o instanceof TransportId)
			return Arrays.equals(id, ((TransportId) o).id);
		return false;
	}
}