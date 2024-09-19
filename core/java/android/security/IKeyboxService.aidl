// IKeyboxService.aidl
package android.security;

interface IKeyboxService {
    /**
     * Sets the Keybox data.
     * @param keyboxData An array of Strings containing Keybox entries.
     */
    void setKeyboxData(in String[] keyboxData);

    /**
     * Retrieves the Keybox data.
     * @return An array of Strings containing Keybox entries.
     */
    String[] getKeyboxData();
}
