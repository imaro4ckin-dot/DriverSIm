package org.example.drivesimui.model;

/**
 * Holds the platform identifier and email for a connected account.
 * The password is NEVER stored as a field; it lives only briefly in
 * memory during the CredentialService encrypt/decrypt call.
 */
public class PlatformCredentials {

    public enum Platform { IRACING, ACC, LMU }

    private Platform platform;
    private String   email;
    private String   displayName;

    public PlatformCredentials() {}

    public PlatformCredentials(Platform platform, String email) {
        this.platform = platform;
        this.email    = email;
    }

    public Platform getPlatform()                  { return platform; }
    public void     setPlatform(Platform platform) { this.platform = platform; }
    public String   getEmail()                     { return email; }
    public void     setEmail(String email)         { this.email = email; }
    public String   getDisplayName()               { return displayName; }
    public void     setDisplayName(String name)    { this.displayName = name; }
}
