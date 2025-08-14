package com.voxstream.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Basic unit test for VoxStream application.
 * Integration tests will be added once Spring configuration is properly set up.
 */
class VoxStreamApplicationTest {

    @Test
    void testApplicationClassExists() {
        // Basic test to ensure our application class is loadable
        Class<?> appClass = VoxStreamApplication.class;
        assertNotNull(appClass);
        assertEquals("VoxStreamApplication", appClass.getSimpleName());
    }

    @Test
    void testApplicationHasMainMethod() throws NoSuchMethodException {
        // Verify the main method exists for proper application launching
        Class<?> appClass = VoxStreamApplication.class;
        assertNotNull(appClass.getDeclaredMethod("main", String[].class));
    }

    @Test
    void javaVersionCompatibility() {
        // Ensure we're running on Java 17+ for macOS Catalina compatibility
        String javaVersion = System.getProperty("java.version");
        assertNotNull(javaVersion);

        // Extract major version number
        String[] versionParts = javaVersion.split("\\.");
        int majorVersion = Integer.parseInt(versionParts[0]);

        assertTrue(majorVersion >= 17, "Java 17+ required for macOS Catalina compatibility. Current: " + javaVersion);
    }

    @Test
    void architecture64Bit() {
        // Verify 64-bit architecture for macOS Catalina compatibility
        String osArch = System.getProperty("os.arch");
        assertNotNull(osArch);

        // Should be 64-bit architecture
        assertTrue(osArch.contains("64") || osArch.equals("aarch64"),
                "64-bit architecture required for macOS Catalina. Current: " + osArch);
    }
}
