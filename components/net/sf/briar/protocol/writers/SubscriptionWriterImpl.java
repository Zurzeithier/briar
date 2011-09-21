package net.sf.briar.protocol.writers;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

import net.sf.briar.api.protocol.Group;
import net.sf.briar.api.protocol.Types;
import net.sf.briar.api.protocol.writers.SubscriptionWriter;
import net.sf.briar.api.serial.Writer;
import net.sf.briar.api.serial.WriterFactory;

class SubscriptionWriterImpl implements SubscriptionWriter {

	private final OutputStream out;
	private final Writer w;

	SubscriptionWriterImpl(OutputStream out, WriterFactory writerFactory) {
		this.out = out;
		w = writerFactory.createWriter(out);
	}

	public void writeSubscriptions(Map<Group, Long> subs, long timestamp)
	throws IOException {
		w.writeUserDefinedId(Types.SUBSCRIPTION_UPDATE);
		w.writeMap(subs);
		w.writeInt64(timestamp);
		out.flush();
	}
}
