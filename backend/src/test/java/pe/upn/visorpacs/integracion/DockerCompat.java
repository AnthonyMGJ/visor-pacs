package pe.upn.visorpacs.integracion;

/**
 * Docker 29 rechaza clientes con API < 1.44 y Testcontainers 1.x anuncia una versión menor
 * (spring-projects/spring-boot#48104). Se fija la versión antes de que Testcontainers cree el cliente.
 * Con Testcontainers 2.x este ajuste ya no hace falta, pero no estorba.
 */
final class DockerCompat {
    private DockerCompat() {}

    static void aplicar() {
        System.setProperty("api.version", "1.44");
    }
}
