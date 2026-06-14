package dev.persefonia.discovery.application.port;

import dev.persefonia.discovery.application.route.PublicRouteLookup;
import dev.persefonia.discovery.application.route.PublicRouteResolution;

public interface ResolvePublicRoutePort {
    PublicRouteResolution resolve(PublicRouteLookup lookup);
}
