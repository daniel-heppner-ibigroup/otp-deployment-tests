package com.arcadis.otpsmoketests.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class DeploymentContextTest {

    @Test
    @DisplayName("Should create deployment context with valid parameters")
    void shouldCreateDeploymentContextWithValidParameters() {
        // Given
        String deploymentName = "SoundTransit";
        String otpUrl = "https://sound-transit-otp.ibi-transit.com";

        // When
        DeploymentContext context = new DeploymentContext(deploymentName, otpUrl);

        // Then
        assertEquals(deploymentName, context.getDeploymentName());
        assertEquals(otpUrl, context.getOtpUrl());
    }

    @Test
    @DisplayName("Should trim whitespace from parameters")
    void shouldTrimWhitespaceFromParameters() {
        // Given
        String deploymentName = "  SoundTransit  ";
        String otpUrl = "  https://sound-transit-otp.ibi-transit.com  ";

        // When
        DeploymentContext context = new DeploymentContext(deploymentName, otpUrl);

        // Then
        assertEquals("SoundTransit", context.getDeploymentName());
        assertEquals("https://sound-transit-otp.ibi-transit.com", context.getOtpUrl());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("Should throw exception for invalid deployment name")
    void shouldThrowExceptionForInvalidDeploymentName(String invalidName) {
        // Given
        String validUrl = "https://sound-transit-otp.ibi-transit.com";

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new DeploymentContext(invalidName, validUrl)
        );
        assertEquals("Deployment name cannot be null or empty", exception.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("Should throw exception for invalid OTP URL")
    void shouldThrowExceptionForInvalidOtpUrl(String invalidUrl) {
        // Given
        String validName = "SoundTransit";

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new DeploymentContext(validName, invalidUrl)
        );
        assertEquals("OTP URL cannot be null or empty", exception.getMessage());
    }

    @Test
    @DisplayName("Should create proper display name")
    void shouldCreateProperDisplayName() {
        // Given
        DeploymentContext context = new DeploymentContext(
            "SoundTransit", 
            "https://sound-transit-otp.ibi-transit.com"
        );

        // When
        String displayName = context.getDisplayName();

        // Then
        assertEquals("SoundTransit (https://sound-transit-otp.ibi-transit.com)", displayName);
    }

    @Test
    @DisplayName("Should correctly identify same deployment")
    void shouldCorrectlyIdentifySameDeployment() {
        // Given
        DeploymentContext context1 = new DeploymentContext(
            "SoundTransit", 
            "https://sound-transit-otp.ibi-transit.com"
        );
        DeploymentContext context2 = new DeploymentContext(
            "SoundTransit", 
            "https://sound-transit-otp.ibi-transit.com"
        );
        DeploymentContext context3 = new DeploymentContext(
            "Hopelink", 
            "https://hopelink-otp.ibi-transit.com"
        );

        // When & Then
        assertTrue(context1.isSameDeployment(context2));
        assertFalse(context1.isSameDeployment(context3));
        assertFalse(context1.isSameDeployment(null));
    }

    @Test
    @DisplayName("Should implement equals and hashCode correctly")
    void shouldImplementEqualsAndHashCodeCorrectly() {
        // Given
        DeploymentContext context1 = new DeploymentContext(
            "SoundTransit", 
            "https://sound-transit-otp.ibi-transit.com"
        );
        DeploymentContext context2 = new DeploymentContext(
            "SoundTransit", 
            "https://sound-transit-otp.ibi-transit.com"
        );
        DeploymentContext context3 = new DeploymentContext(
            "Hopelink", 
            "https://hopelink-otp.ibi-transit.com"
        );

        // When & Then
        assertEquals(context1, context2);
        assertNotEquals(context1, context3);
        assertEquals(context1.hashCode(), context2.hashCode());
        assertNotEquals(context1.hashCode(), context3.hashCode());
        
        // Test reflexivity
        assertEquals(context1, context1);
        
        // Test null and different class
        assertNotEquals(context1, null);
        assertNotEquals(context1, "not a deployment context");
    }

    @Test
    @DisplayName("Should have meaningful toString representation")
    void shouldHaveMeaningfulToStringRepresentation() {
        // Given
        DeploymentContext context = new DeploymentContext(
            "SoundTransit", 
            "https://sound-transit-otp.ibi-transit.com"
        );

        // When
        String toString = context.toString();

        // Then
        assertTrue(toString.contains("SoundTransit"));
        assertTrue(toString.contains("https://sound-transit-otp.ibi-transit.com"));
        assertTrue(toString.contains("DeploymentContext"));
    }
}