package net.bp4k.locationlens.service;

/** Boundary shared with future place services; returns the provider's canonical ID. */
public interface BusinessPlaceLookup {
    String requireBusinessPlace(String placeId);
}
