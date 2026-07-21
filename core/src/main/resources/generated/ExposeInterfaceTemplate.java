package <PACKAGE>;

import com.google.common.base.Preconditions;

public interface <PLUGIN>API {
	<el>
	<TYPE> get<TYPE_NAME>();
	</el>

	/**
	 * Retrieves the instance of the <PLUGIN>API. This method will throw an exception if the instance has not been set yet.
	 *
	 * @return The instance of the <PLUGIN>API
	 * @throws IllegalStateException if the instance has not been set yet
	 */
	static <PLUGIN>API getInstance() {
		Preconditions.checkNotNull(Holder.instance, "<PLUGIN>API instance has not been set yet");
		return Holder.instance;
	}

	final class Holder {
		private static <PLUGIN>API instance;

		private Holder() {}

		/**
		 * Sets the instance of the <PLUGIN>API. This method should only be called once during plugin initialization.
		 *
		 * @param newInstance The instance of the <PLUGIN>API to set
		 * @throws IllegalArgumentException if the instance has already been set or if the newInstance is null
		 */
		static void setInstance(final <PLUGIN>API newInstance) {
			Preconditions.checkNotNull(newInstance, "<PLUGIN>API instance cannot be null");
			Preconditions.checkArgument(instance == null, "<PLUGIN>API instance has already been set");
			Holder.instance = newInstance;
		}
	}
}