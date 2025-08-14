package com.voxstream.platform.api.credentials;

/** Marker for platform credential types (e.g., OAuth tokens). */
public interface PlatformCredential {
    /**
     * @return display-safe identifier (never the secret itself)
     */
    String id();
}
