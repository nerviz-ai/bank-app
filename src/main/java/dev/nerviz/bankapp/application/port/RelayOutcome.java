package dev.nerviz.bankapp.application.port;

/**
 * What one relay pass did, so whoever runs it can count dead letters without the application
 * layer registering a meter.
 */
public record RelayOutcome(int published, int failed, int deadLettered) {

    private static final RelayOutcome NONE = new RelayOutcome(0, 0, 0);

    public static RelayOutcome none() {
        return NONE;
    }

    public static RelayOutcome publishedOne() {
        return new RelayOutcome(1, 0, 0);
    }

    public static RelayOutcome failedOne() {
        return new RelayOutcome(0, 1, 0);
    }

    public static RelayOutcome deadLetteredOne() {
        return new RelayOutcome(0, 0, 1);
    }

    public RelayOutcome plus(RelayOutcome other) {
        return new RelayOutcome(published + other.published, failed + other.failed, deadLettered + other.deadLettered);
    }
}
