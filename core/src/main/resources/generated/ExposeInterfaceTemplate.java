package <PACKAGE>;

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
		return Holder.getInstance();
	}

	/**
	 * Holds the instance of the <PLUGIN>API.
	 * <p>
	 * Only the generated module that publishes the API implementation can set it, every other caller goes through
	 * {@link <PLUGIN>API#getInstance()}.
	 */
	final class Holder {
		private static <PLUGIN>API instance;

		private Holder() {
		}

		static <PLUGIN>API getInstance() {
			final var current = instance;
			if (current == null) {
				throw new IllegalStateException("<PLUGIN>API instance has not been set yet");
			}

			return current;
		}

		/**
		 * Sets the instance of the <PLUGIN>API. This method should only be called once during plugin initialization.
		 *
		 * @param newInstance The instance of the <PLUGIN>API to set
		 * @throws IllegalArgumentException if the instance has already been set or if the newInstance is null
		 */
		static void setInstance(final <PLUGIN>API newInstance) {
			if (newInstance == null) {
				throw new IllegalArgumentException("<PLUGIN>API instance cannot be null");
			}

			if (instance != null) {
				throw new IllegalArgumentException("<PLUGIN>API instance has already been set");
			}

			Holder.instance = newInstance;
		}
	}
}
