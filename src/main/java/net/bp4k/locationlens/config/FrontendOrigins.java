package net.bp4k.locationlens.config;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Origins allowed to receive session tokens, derived from the login allow-list. */
@Component
public class FrontendOrigins {
    private final List<String> origins;

    public FrontendOrigins(@Value("${net.bp4k.locationlens.redirect-uri:}") String redirects) {
        origins = Arrays.stream(redirects.split(",")).map(String::trim)
                .map(FrontendOrigins::originOf).filter(Objects::nonNull).distinct().toList();
    }

    private static String originOf(String target) {
        try {
            URI uri = URI.create(target);
            String scheme = uri.getScheme();
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    || uri.getHost() == null || uri.getUserInfo() != null) return null;
            scheme = scheme.toLowerCase(Locale.ROOT);
            int port = uri.getPort();
            boolean defaultPort = port == -1 || (port == 80 && scheme.equals("http"))
                    || (port == 443 && scheme.equals("https"));
            return scheme + "://" + uri.getHost().toLowerCase(Locale.ROOT) + (defaultPort ? "" : ":" + port);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    public List<String> values() { return origins; }
    public boolean allows(String origin) { return origin != null && origins.contains(origin); }
}
