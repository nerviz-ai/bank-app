package dev.nerviz.bankapp.infrastructure.rest;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import org.springframework.http.server.observation.DefaultServerRequestObservationConvention;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.accept.ApiVersionHolder;
import org.springframework.web.servlet.HandlerMapping;

/**
 * The resolved API version as a tag of {@code http.server.requests}, so use per version is
 * measured before a deprecation window is chosen ({@.claude/rules/api-rest.md} § Versioning).
 * Bounded by the supported list: the parsed version, or {@code none} outside {@code /api/} and
 * for an unsupported one — never the raw path segment, which the client chooses.
 */
@Component
public class ApiVersionObservationConvention extends DefaultServerRequestObservationConvention {

    private static final String TAG = "api.version";
    private static final String NONE = "none";

    @Override
    public KeyValues getLowCardinalityKeyValues(ServerRequestObservationContext context) {
        return super.getLowCardinalityKeyValues(context).and(KeyValue.of(TAG, versionOf(context)));
    }

    private static String versionOf(ServerRequestObservationContext context) {
        Object attribute = context.getCarrier().getAttribute(HandlerMapping.API_VERSION_ATTRIBUTE);
        if (attribute instanceof ApiVersionHolder holder && holder.hasVersion()) {
            return holder.getVersion().toString();
        }
        return NONE;
    }
}
