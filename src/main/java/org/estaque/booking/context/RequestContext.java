package org.estaque.booking.context;

/** Identifiant de la requête en cours, hérité par les sous-tâches d'un StructuredTaskScope. */
public final class RequestContext {

    public static final ScopedValue<String> REQUEST_ID = ScopedValue.newInstance();

    private RequestContext() {}

    public static void log(String message) {
        System.out.printf("[%s] %s%n", REQUEST_ID.orElse("no-request"), message);
    }
}
