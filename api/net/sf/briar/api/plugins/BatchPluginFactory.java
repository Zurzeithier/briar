package net.sf.briar.api.plugins;

import java.util.concurrent.Executor;

public interface BatchPluginFactory {

	BatchPlugin createPlugin(Executor executor,
			BatchPluginCallback callback);
}